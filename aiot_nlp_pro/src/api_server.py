import os
import sys
from pathlib import Path
from typing import List, Dict, Any, Optional

try:
    from rag_engine import HybridRagEngine
    from llm_engine import LlmEngine
except ImportError:
    sys.path.insert(0, str(Path(__file__).parent / "src"))
    from rag_engine import HybridRagEngine
    from llm_engine import LlmEngine

from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

# ================= 配置与初始化 =================

app = FastAPI(
    title="AI Teacher RAG API",
    description="基于混合检索和 LLM 的智能问答接口",
    version="1.0.0"
)

# 全局变量存储引擎实例
rag_engine: Optional[HybridRagEngine] = None
llm_engine: Optional[LlmEngine] = None

# 系统配置
SYSTEM_CONFIG = {
    "embedding_model": "BAAI/bge-small-zh-v1.5",
    "model_provider": "ollama",
    "model_name": "qwen2.5:7b-instruct-q4_K_M", 
    "ollama_base_url": "http://localhost:11434",
    "data_directory": "./data",
    "cache_directory": "./data_cache"
}

def initialize_system():
    """初始化 RAG 和 LLM 引擎"""
    global rag_engine, llm_engine
    
    print("🚀 Initializing System...")
    
    # 1. 加载文档
    data_dir = SYSTEM_CONFIG.get("data_directory", "./data")
    raw_docs = []
    data_path = Path(data_dir)
    
    if data_path.exists():
        for file_path in data_path.iterdir():
            if file_path.is_file():
                loader = None
                if file_path.suffix.lower() == '.pdf':
                    loader = PyPDFLoader(str(file_path))
                elif file_path.suffix.lower() == '.docx':
                    loader = Docx2txtLoader(str(file_path))
                elif file_path.suffix.lower() == '.txt':
                    loader = TextLoader(str(file_path), encoding='utf-8')
                
                if loader:
                    try:
                        docs = loader.load()
                        raw_docs.extend(docs)
                    except Exception as e:
                        print(f"⚠️ Failed to load {file_path.name}: {e}")
    
    # 2. 切分文档
    processed_docs = []
    if raw_docs:
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=500, 
            chunk_overlap=50,
            separators=["\n\n", "\n", "。", "！", "？", " ", ""]
        )
        processed_docs = splitter.split_documents(raw_docs)
        print(f"✅ Processed {len(processed_docs)} document chunks.")
    
    # 3. 初始化引擎
    rag_engine = HybridRagEngine(SYSTEM_CONFIG)
    if processed_docs:
        rag_engine.ingest_documents(processed_docs, index_path=SYSTEM_CONFIG.get("cache_directory", "./data_cache"))
    else:
        print("⚠️ No documents found to index.")
    
    llm_engine = LlmEngine(SYSTEM_CONFIG)
    print("✅ System Initialization Complete.")

# ================= 数据模型定义 =================

class QueryRequest(BaseModel):
    question: str
    topK: int = 5
    sessionId: Optional[str] = "default_session"
    useCoT: bool = False  # 可选：是否启用思维链

class SourceItem(BaseModel):
    content: str
    source: str
    page: Optional[int] = None

class QueryResponse(BaseModel):
    answer: str
    sources: List[SourceItem]
    sessionId: str
    model_used: str

# ================= API 接口实现 =================

@app.on_event("startup")
async def startup_event():
    """服务启动时自动初始化系统"""
    initialize_system()

@app.post("/api/retrieve_and_answer", response_model=QueryResponse)
async def retrieve_and_answer(request: QueryRequest):
    """
    核心接口：接收问题，检索文档，生成答案
    """
    if not rag_engine or not llm_engine:
        raise HTTPException(status_code=503, detail="System not initialized yet")
    
    if not request.question.strip():
        raise HTTPException(status_code=400, detail="Question cannot be empty")
    
    # 1. 执行检索
    # 这里可以加入动态 Top-K 逻辑，或者直接使用请求中的 topK
    k = request.topK if request.topK > 0 else 5
    context_docs = rag_engine.retrieve(request.question, k=k)
    
    # 2. 构建 Prompt
    system_prompt = """你是一个专业的 AI 教师。请严格依据提供的【上下文】回答问题。
    如果上下文中没有答案，请直接告知用户你不知道，不要编造。
    回答要清晰、准确，并尽量引用原文来源。"""
    
    if request.useCoT:
        system_prompt = """你是一位拥有深厚学科知识的 AI 教师助手。请先进行内部逻辑推导（思维链），然后给出最终答案。
        输出格式要求：
        ### 思考过程
        (简要列出推导步骤)
        ### 最终回答
        (正式答案)
        """
    
    context_text = ""
    if context_docs:
        context_text = "\n\n".join([f"[来源{i+1}]: {doc.page_content}" for i, doc in enumerate(context_docs)])
        full_prompt = f"【上下文】:\n{context_text}\n\n【用户问题】:\n{request.question}"
    else:
        full_prompt = request.question
        system_prompt += "\n注意：未提供相关上下文，请仅依靠你的通用知识回答，并提示用户资料可能不足。"
    
    # 3. 调用 LLM 生成
    try:
        response_text = llm_engine.generate(full_prompt, system_prompt=system_prompt)
        
        # 如果启用了 CoT 但前端不需要显示思考过程，可以在这里做简单的文本清洗
        # (此处保留原始返回，由前端决定是否展示思考过程，或者在此处用正则剥离)
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM Generation Error: {str(e)}")
    
    # 4. 格式化参考来源
    sources_list = []
    for doc in context_docs:
        sources_list.append(SourceItem(
            content=doc.page_content[:200] + "...", # 截取部分内容避免返回过长
            source=os.path.basename(doc.metadata.get('source', 'Unknown')),
            page=doc.metadata.get('page', None)
        ))
    
    return QueryResponse(
        answer=response_text,
        sources=sources_list,
        sessionId=request.sessionId,
        model_used=SYSTEM_CONFIG.get("model_name", "unknown")
    )

@app.get("/health")
async def health_check():
    return {"status": "healthy", "rag_ready": rag_engine is not None, "llm_ready": llm_engine is not None}

if __name__ == "__main__":
    # 启动命令：uvicorn api_server:app --host 0.0.0.0 --port 8000 --reload
    uvicorn.run(app, host="0.0.0.0", port=8000)
