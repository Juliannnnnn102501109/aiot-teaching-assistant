import os
import pickle
import numpy as np
from typing import List, Dict, Any
from langchain_core.documents import Document
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS
from rank_bm25 import BM25Okapi
import jieba

class HybridRagEngine:
    def __init__(self, config: Dict[str, Any]):
        """
        初始化混合检索引擎
        :param config: 配置字典，包含 embedding_model 等信息
        """
        self.config = config
        
        # 1. 初始化 Embedding 模型 (用于向量检索)
        embedding_model_name = config.get("embedding_model", "BAAI/bge-small-zh-v1.5")
        print(f"📥 Loading Embedding Model: {embedding_model_name} ...")
        
        # 自动检测设备，如果有 cuda 则使用，否则 cpu
        device = "cuda" if self._check_cuda() else "cpu"
        print(f"   -> Using device: {device}")
        
        self.embeddings = HuggingFaceEmbeddings(
            model_name=embedding_model_name,
            model_kwargs={'device': device},
            encode_kwargs={'normalize_embeddings': True}
        )
        
        # 初始化占位符
        self.vector_store = None
        self.bm25_index = None
        self.bm25_docs = []
        self.all_docs = []
        
        # 分词工具 (Jieba)
        # 如果有自定义词典，可以在这里加载：jieba.load_userdict("my_dict.txt")
        self.tokenizer = lambda x: list(jieba.cut(x))
        
        print("✅ HybridRagEngine initialized.")

    def _check_cuda(self) -> bool:
        """简单检查是否有可用的 CUDA"""
        try:
            import torch
            return torch.cuda.is_available()
        except ImportError:
            return False

    def ingest_documents(self, documents: List[Document], index_path: str = "./data_cache"):
        """
        摄入文档：
        1. 检查缓存是否存在，存在则直接加载。
        2. 不存在则构建向量索引和 BM25 索引，并保存到磁盘。
        
        :param documents: 文档列表
        :param index_path: 缓存保存路径
        """
        os.makedirs(index_path, exist_ok=True)
        faiss_index_file = os.path.join(index_path, "index.faiss")
        bm25_cache_file = os.path.join(index_path, "bm25_cache.pkl")
        
        # --- 尝试加载缓存 ---
        if os.path.exists(faiss_index_file) and os.path.exists(bm25_cache_file):
            print("⚡ Found cached indices. Loading from disk...")
            try:
                self._load_indices(faiss_index_file, bm25_cache_file)
                print("✅ Cache loaded successfully. Skipping build process.")
                return
            except Exception as e:
                print(f"⚠️ Cache loading failed ({e}). Rebuilding indices...")

        # --- 构建新索引 ---
        if not documents:
            print("⚠️ No documents provided to ingest.")
            return

        print(f"📚 Building new indices for {len(documents)} documents...")
        self.all_docs = documents
        
        # 1. 构建向量索引 (FAISS)
        print("   -> Building Vector Index (FAISS)...")
        self.vector_store = FAISS.from_documents(documents, self.embeddings)
        
        # 2. 构建 BM25 索引
        print("   -> Building BM25 Index (with Jieba)...")
        tokenized_docs = []
        self.bm25_docs = []
        
        # 进度提示
        total = len(documents)
        for i, doc in enumerate(documents):
            tokens = self.tokenizer(doc.page_content)
            tokenized_docs.append(tokens)
            self.bm25_docs.append(doc)
            if (i + 1) % 100 == 0 or (i + 1) == total:
                print(f"      Processed {i+1}/{total} docs...")
                
        self.bm25_index = BM25Okapi(tokenized_docs)
        
        # 3. 保存缓存到磁盘
        print("   -> Saving indices to disk for faster startup next time...")
        try:
            # 保存 FAISS
            self.vector_store.save_local(faiss_index_file)
            
            # 保存 BM25 对象和文档列表 (因为 rank-bm25 没有内置 save/load)
            with open(bm25_cache_file, 'wb') as f:
                pickle.dump({
                    'bm25_index': self.bm25_index,
                    'bm25_docs': self.bm25_docs
                }, f)
            
            print("✅ Indices built and cached successfully!")
        except Exception as e:
            print(f"❌ Failed to save cache: {e}")

    def _load_indices(self, faiss_path: str, bm25_path: str):
        """
        从磁盘加载索引
        """
        # 加载 FAISS
        self.vector_store = FAISS.load_local(
            faiss_path, 
            self.embeddings, 
            allow_dangerous_deserialization=True # 信任本地生成的文件
        )
        
        # 加载 BM25
        with open(bm25_path, 'rb') as f:
            data = pickle.load(f)
            self.bm25_index = data['bm25_index']
            self.bm25_docs = data['bm25_docs']

    def retrieve(self, query: str, k: int = 5) -> List[Document]:
        """
        执行混合检索 (Hybrid Search) + RRF (Reciprocal Rank Fusion)
        
        :param query: 用户查询
        :param k: 最终返回的文档数量
        :return: 排序后的文档列表
        """
        if not self.vector_store or not self.bm25_index:
            print("⚠️ Retrieval failed: Indices not loaded.")
            return []

        # --- 1. 向量检索 ---
        # 为了 RRF 效果，我们检索比 k 更多的文档 (例如 2*k 或固定 20)
        top_n = max(k * 2, 20) 
        try:
            vector_docs = self.vector_store.similarity_search(query, k=top_n)
        except Exception:
            vector_docs = []

        # --- 2. BM25 检索 ---
        query_tokens = self.tokenizer(query)
        try:
            bm25_scores = self.bm25_index.get_scores(query_tokens)
            # 获取分数最高的 top_n 个索引
            top_bm25_indices = np.argsort(bm25_scores)[::-1][:top_n]
            bm25_docs = [self.bm25_docs[i] for i in top_bm25_indices if i < len(self.bm25_docs)]
        except Exception:
            bm25_docs = []

        # --- 3. RRF 融合 (Reciprocal Rank Fusion) ---
        # 创建一个字典来存储每个文档的 RRF 分数
        # Key: document content (作为唯一标识), Value: {'doc': Document, 'score': float}
        doc_map = {}
        
        # 辅助函数：更新 RRF 分数
        def update_rrf(docs_list, rank_constant=60):
            for rank, doc in enumerate(docs_list, start=1):
                content = doc.page_content
                if content not in doc_map:
                    doc_map[content] = {'doc': doc, 'score': 0.0}
                # RRF 公式: 1 / (k + rank)
                doc_map[content]['score'] += 1.0 / (rank_constant + rank)

        update_rrf(vector_docs)
        update_rrf(bm25_docs)

        # --- 4. 排序并返回 Top K ---
        # 按 RRF 分数降序排序
        sorted_docs = sorted(doc_map.values(), key=lambda x: x['score'], reverse=True)
        
        # 提取前 k 个文档对象
        final_results = [item['doc'] for item in sorted_docs[:k]]
        
        return final_results


