# <center>🎓 AI 教师助手

本项目是一个基于 RAG (检索增强生成) 的智能问答系统，专为教育场景设计。
**核心特性**：
- 🚀 **高性能**：索引持久化缓存，二次启动秒级加载。
- 🧠 **混合检索**：结合 Vector (语义) + BM25 (关键词/Jieba)，提高召回率。
- ⚡ **动态策略**：根据问题复杂度自动调整检索数量 (Top-K)。
- 💾 **轻量模型**：默认使用 Qwen2.5 7B (Int4 量化)，降低显存占用。

## 🛠️ 前置要求 (Prerequisites)

1. **Python 环境**: Python 3.9 - 3.11
2. **Ollama 服务**: 
   - 安装 Ollama: https://ollama.com
   - **必须拉取指定模型** (在终端执行):
     ```bash
     ollama pull qwen2.5:7b-instruct-q4_K_M
     ```
   - 确保 Ollama 服务正在运行 (`ollama serve`)。

## 🚀 快速开始 (Quick Start)

### 1. 安装依赖
```bash
pip install -r requirements.txt
```

### 2. 准备数据
将你的课件 (PDF, DOCX, TXT) 放入项目根目录下的 `data` 文件夹中。
*(如果没有 data 文件夹，请手动创建)*

### 3. 启动服务
```bash
python api_server.py
```

### 4. 首次运行说明
- 第一次运行时，系统会下载 Embedding 模型并构建索引，可能需要 1-2 分钟。
- 构建完成后，索引会自动保存到 `./data_cache`。
- **第二次运行**时，系统将直接加载缓存，启动时间 < 1 秒。

## 📂 项目结构说明

- `app.py`: Streamlit 前端入口，包含动态 Top-K 逻辑。
- `src/rag_engine.py`: 核心检索引擎 (FAISS + BM25 + Jieba + 缓存管理)。
- `src/llm_engine.py`: LLM 交互逻辑 (对接 Ollama)。
- `src/api_server.py`: 独立的入口文件，不依赖 Streamlit，专门用于提供 HTTP 服务。
- `data/`: 存放原始文档。
- `data_cache/`: (自动生成) 存放构建好的向量索引和 BM25 缓存。

## ⚙️ 配置项

如需修改模型或路径，请编辑 `app.py` 中的 `simple_config` 字典：
- `model_name`: 默认为 `qwen2.5:7b-instruct-q4_K_M`
- `embedding_model`: 默认为 `BAAI/bge-small-zh-v1.5`
- `data_directory`: 默认为 `./data`
