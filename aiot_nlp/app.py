import streamlit as st
import sys
from pathlib import Path
import os
import re

# 配置页面
st.set_page_config(
    page_title="AI 教师助手 - 性能优化版",
    page_icon="🚀",
    layout="wide"
)

# 添加 src 到路径
sys.path.insert(0, str(Path(__file__).parent / "src"))

# 导入自定义模块
from rag_engine import HybridRagEngine
from llm_engine import LlmEngine
from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter

# ================= 辅助函数：动态 Top-K =================
def get_dynamic_top_k(query: str) -> int:
    """
    根据查询的复杂度和长度动态调整检索文档数量 (Top-K)
    目的：简单问题少检索（快），复杂问题多检索（准）
    """
    query_lower = query.lower()
    
    # 1. 短查询通常很具体，不需要太多上下文
    if len(query.strip()) < 8:
        return 3
    
    # 2. 检测复杂意图关键词
    complex_keywords = [
        "比较", "区别", "对比", "分析", "总结", "概述", 
        "为什么", "如何", "怎样", "流程", "步骤", 
        "优缺点", "关系", "影响", "原理"
    ]
    
    if any(keyword in query_lower for keyword in complex_keywords):
        return 8  # 复杂问题需要更多上下文支撑
    
    # 3. 默认情况
    return 5

# ================= 缓存初始化逻辑 =================
@st.cache_resource
def load_system(_config):
    """
    只加载一次系统资源 (模型、索引)，避免每次交互都重新加载
    """
    with st.spinner("🚀 正在初始化 AI 教师系统 (加载模型 + 构建索引)..."):
        # 1. 加载文档
        data_dir = _config.get("data_directory", "./data")
        raw_docs = []
        data_path = Path(data_dir)
        
        if data_path.exists():
            files_processed = 0
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
                            files_processed += 1
                        except Exception as e:
                            st.warning(f"跳过文件 {file_path.name}: {e}")
            
            if files_processed == 0:
                st.warning("⚠️ 未在 `data` 文件夹中找到任何 PDF, DOCX 或 TXT 文件。系统将无法回答问题。")
        
        # 2. 切分文档
        processed_docs = []
        if raw_docs:
            splitter = RecursiveCharacterTextSplitter(
                chunk_size=500, 
                chunk_overlap=50,
                separators=["\n\n", "\n", "。", "！", "？", " ", ""]
            )
            processed_docs = splitter.split_documents(raw_docs)
            print(f"✅ 文档切分完成：共 {len(processed_docs)} 个片段")
        
        # 3. 初始化引擎
        rag_engine = HybridRagEngine(_config)
        if processed_docs:
            # 传入缓存路径，启用持久化
            rag_engine.ingest_documents(processed_docs, index_path="./data_cache")
        else:
            print("⚠️ 没有文档可索引")
        
        llm_engine = LlmEngine(_config)
        
        return rag_engine, llm_engine, len(processed_docs)

# ================= 侧边栏配置 =================
st.sidebar.title("⚙️ 系统配置")
st.sidebar.markdown("### 🚀 性能优化版")
st.sidebar.info("""
- **模型**: Qwen2.5 7B (Int4)
- **检索**: 混合检索 (Vector + BM25) 
- **策略**: 动态 Top-K (根据问题复杂度自动调整)
""")

st.sidebar.markdown("---")
st.sidebar.write("📂 **数据状态**: 请确保根目录有 `data` 文件夹并放入课件。")

# ================= 主界面 =================
st.title("🎓 AI 教师助手 (高性能版)")
st.markdown("基于 **RAG + 混合检索 + 动态上下文** 的智能问答系统")

# 初始化系统配置
simple_config = {
    "embedding_model": "BAAI/bge-small-zh-v1.5",
    # ✅ 修改点：指定量化模型
    "model_provider": "ollama",
    "model_name": "qwen2.5:7b-instruct-q4_K_M", 
    "ollama_base_url": "http://localhost:11434",
    "data_directory": "./data"
}

try:
    rag_engine, llm_engine, doc_count = load_system(simple_config)
    status_msg = f"✅ 系统就绪！已加载 **{doc_count}** 个文档片段。"
    if doc_count == 0:
        status_msg += " (⚠️ 未检测到文档，请先放入文件)"
    st.success(status_msg)
    
except Exception as e:
    st.error(f"❌ 系统初始化失败: {e}")
    st.stop()

# ================= 聊天界面 =================
if "messages" not in st.session_state:
    st.session_state.messages = []

# 显示历史消息
for message in st.session_state.messages:
    with st.chat_message(message["role"]):
        st.markdown(message["content"])
        if "context_stats" in message:
            # 显示检索统计信息
            stats = message["context_stats"]
            st.caption(f"🔍 检索策略: 动态 Top-K={stats['k']} | 来源数: {stats['source_count']}")
            
        if "context" in message and st.checkbox("查看参考来源", key=f"ctx_{message['role']}_{id(message)}"):
            with st.expander("📄 点击查看详细参考片段"):
                for i, ctx in enumerate(message["context"]):
                    source = ctx.metadata.get('source', 'Unknown')
                    # 提取文件名
                    fname = Path(source).name if source else "Memory"
                    st.markdown(f"**片段 {i+1}** (来源: `{fname}`)")
                    st.text(ctx.page_content[:300] + ("..." if len(ctx.page_content) > 300 else ""))
                    st.divider()

# 用户输入
if prompt := st.chat_input("请输入关于课程的问题..."):
    # 1. 显示用户消息
    with st.chat_message("user"):
        st.markdown(prompt)
    st.session_state.messages.append({"role": "user", "content": prompt})

    # 2. 生成回答
    with st.chat_message("assistant"):
        message_placeholder = st.empty()
        
        # 计算动态 Top-K
        dynamic_k = get_dynamic_top_k(prompt)
        message_placeholder.markdown(f"🧠 正在思考 (动态检索 Top-{dynamic_k})...")
        
        try:
            # ✅ 修改点：使用动态计算的 k 进行检索
            context_docs = rag_engine.retrieve(prompt, k=dynamic_k)
            
            # 构造上下文文本
            if context_docs:
                context_text = "\n\n".join([f"[来源{i+1}]: {doc.page_content}" for i, doc in enumerate(context_docs)])
                system_prompt = "你是一个专业的 AI 教师。请严格依据提供的【上下文】回答问题。如果上下文中没有答案，请直接告知用户你不知道，不要编造。"
                full_prompt = f"【上下文】:\n{context_text}\n\n【问题】: {prompt}"
            else:
                context_text = ""
                system_prompt = "你是一个专业的 AI 教师。由于没有相关文档，请仅凭你的通用知识回答，但需注明这并非来自课程资料。"
                full_prompt = prompt
            
            # 流式输出 (如果 llm_engine 支持流式，这里简化为非流式以保持代码稳定，若需流式需修改 llm_engine)
            # 假设 llm_engine.generate 是同步返回
            response = llm_engine.generate(full_prompt, system_prompt=system_prompt)
            
            message_placeholder.markdown(response)
            
            # 保存历史记录 (包含检索统计)
            st.session_state.messages.append({
                "role": "assistant", 
                "content": response,
                "context": context_docs,
                "context_stats": {
                    "k": dynamic_k,
                    "source_count": len(set([d.metadata.get('source') for d in context_docs]))
                }
            })
            
        except Exception as e:
            st.error(f"生成回答时出错: {e}")
            import traceback
            st.code(traceback.format_exc())


