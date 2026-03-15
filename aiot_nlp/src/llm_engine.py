"""
LLM Engine - 统一的大语言模型接口
支持本地 Ollama、云端 API 以及智能 Mock 降级模式
"""

import os
import json
import time
from typing import Optional, Dict, Any, List
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

# 尝试导入其他提供商 (可选)
try:
    from langchain_community.llms import Tongyi # 通义千问示例
    HAS_TONGYI = True
except ImportError:
    HAS_TONGYI = False

class LlmEngine:
    """统一 LLM 引擎，支持自动降级"""
    
    def __init__(self, config: Dict[str, Any]):
        """
        初始化 LLM 引擎
        
        Args:
            config: 配置字典，包含 model_provider, model_name, api_key 等
        """
        self.config = config
        self.provider = config.get("model_provider", "ollama")
        self.model_name = config.get("model_name", "qwen2.5:7b")
        self.api_key = config.get("api_key", "")
        self.api_base = config.get("api_base", "")
        self.temperature = config.get("temperature", 0.7)
        
        self.llm = None
        self.is_mock_mode = False
        
        self._initialize_model()
    
    def _initialize_model(self):
        """初始化模型，失败则自动进入 Mock 模式"""
        print(f"🚀 Initializing LLM: {self.provider} / {self.model_name}")
        
        try:
            if self.provider == "ollama":
                self.llm = ChatOllama(
                    model=self.model_name,
                    base_url=self.config.get("ollama_base_url", "http://localhost:11434"),
                    temperature=self.temperature
                )
            elif self.provider == "openai":
                if not self.api_key:
                    raise ValueError("OpenAI API Key is missing")
                self.llm = ChatOpenAI(
                    model=self.model_name,
                    api_key=self.api_key,
                    base_url=self.api_base or None,
                    temperature=self.temperature
                )
            elif self.provider == "zhipu":
                # 示例：智谱 AI (需安装 zhipuai 或通过 openai 兼容接口)
                if not self.api_key:
                    raise ValueError("Zhipu API Key is missing")
                self.llm = ChatOpenAI(
                    model=self.model_name,
                    api_key=self.api_key,
                    base_url="https://open.bigmodel.cn/api/paas/v4",
                    temperature=self.temperature
                )
            else:
                raise ValueError(f"Unsupported provider: {self.provider}")
            
            # 简单测试连接 (可选，耗时操作可省略)
            # self.llm.invoke("Hello") 
            print(f"✅ LLM initialized successfully: {self.provider}")
            
        except Exception as e:
            print(f"⚠️ Failed to initialize real LLM: {e}")
            print("🔄 Switching to MOCK mode for stability...")
            self.is_mock_mode = True
            self.llm = None
    
    def generate(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        """
        生成回复
        
        Args:
            prompt: 用户提示
            system_prompt: 系统提示 (可选)
            
        Returns:
            生成的文本
        """
        if self.is_mock_mode:
            return self._mock_generate(prompt, system_prompt)
        
        try:
            if isinstance(self.llm, ChatOpenAI) or hasattr(self.llm, 'invoke'):
                # 构建消息列表
                messages = []
                if system_prompt:
                    messages.append(SystemMessage(content=system_prompt))
                messages.append(HumanMessage(content=prompt))
                
                response = self.llm.invoke(messages)
                # 处理不同返回类型
                if hasattr(response, 'content'):
                    return response.content
                return str(response)
            else:
                # 传统 LLM 接口
                full_prompt = f"{system_prompt}\n\n{prompt}" if system_prompt else prompt
                return self.llm.invoke(full_prompt)
                
        except Exception as e:
            print(f"❌ LLM Generation Error: {e}")
            print("🔄 Falling back to MOCK mode for this request...")
            # 临时降级
            return self._mock_generate(prompt, system_prompt)
    
    def generate_json(self, prompt: str, system_prompt: Optional[str] = None) -> Dict:
        """
        生成 JSON 格式的回复 (带重试机制)
        
        Args:
            prompt: 用户提示
            system_prompt: 系统提示
            
        Returns:
            解析后的字典
        """
        default_json = {"error": "Failed to generate JSON", "fallback": True}
        
        if self.is_mock_mode:
            mock_data = self._mock_generate(prompt, system_prompt)
            # 尝试从 Mock 文本中提取 JSON
            return self._extract_json_from_text(mock_data) or default_json
        
        # 增强 Prompt 要求输出 JSON
        json_instruction = "\n\n请仅输出标准的 JSON 格式，不要包含 markdown 代码块标记 (如 ```json)。"
        full_system = (system_prompt or "") + json_instruction
        
        max_retries = 3
        for i in range(max_retries):
            try:
                raw_text = self.generate(prompt, full_system)
                result = self._extract_json_from_text(raw_text)
                if result:
                    return result
                print(f"⚠️ Retry {i+1}: Invalid JSON format received")
            except Exception as e:
                print(f"⚠️ Retry {i+1}: Error parsing JSON - {e}")
            
            time.sleep(1) # 短暂等待后重试
            
        return default_json
    
    def _extract_json_from_text(self, text: str) -> Optional[Dict]:
        """从文本中提取 JSON 对象"""
        if not text:
            return None
        
        # 去除 markdown 标记
        text = text.replace("```json", "").replace("```", "").strip()
        
        # 尝试直接解析
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass
        
        # 尝试查找第一个 { 和最后一个 }
        start = text.find("{")
        end = text.rfind("}")
        
        if start != -1 and end != -1 and end > start:
            try:
                return json.loads(text[start:end+1])
            except json.JSONDecodeError:
                pass
        
        return None
    
    def _mock_generate(self, prompt: str, system_prompt: Optional[str] = None) -> str:
        """Mock 模式生成器，用于测试和降级"""
        print("🤖 [MOCK MODE] Generating response...")
        
        prompt_lower = prompt.lower()
        
        # 简单的规则匹配模拟智能
        if "json" in (system_prompt or "").lower() or "structure" in prompt_lower:
            return json.dumps({
                "status": "mock_success",
                "message": "This is a mock response because the real LLM is unavailable.",
                "data": {
                    "topic": "Mock Topic",
                    "points": ["Point 1", "Point 2", "Point 3"]
                }
            }, ensure_ascii=False)
        
        if "hello" in prompt_lower or "hi" in prompt_lower:
            return "Hello! I am your AI Teacher (Mock Mode). How can I help you today?"
        
        if "python" in prompt_lower:
            return "Python is a high-level programming language known for its simplicity and readability. (Mock Response)"
        
        return f"This is a mock response to: '{prompt[:50]}...' \n[System Note: Real LLM is currently unavailable.]"
    
    def is_available(self) -> bool:
        """检查 LLM 是否可用（非 Mock 模式）"""
        return not self.is_mock_mode and self.llm is not None


# ==================== 测试代码 ====================
if __name__ == "__main__":
    # 模拟配置
    test_config = {
        "model_provider": "ollama",
        "model_name": "qwen2.5:latest", # 如果本地没有，会自动切 Mock
        "ollama_base_url": "http://localhost:11434"
    }
    
    engine = LlmEngine(test_config)
    
    print("\n--- Test 1: Basic Generation ---")
    resp = engine.generate("你好，请介绍一下 Python。")
    print(f"Response: {resp}")
    
    print("\n--- Test 2: JSON Generation ---")
    json_resp = engine.generate_json(
        prompt="创建一个关于人工智能的教学大纲",
        system_prompt="你是一个课程设计师，输出 JSON 格式"
    )
    print(f"JSON Response: {json.dumps(json_resp, indent=2, ensure_ascii=False)}")
    
    print("\n--- Test 3: Status Check ---")
    print(f"Is Real LLM Available? {engine.is_available()}")
    print(f"Is Mock Mode? {engine.is_mock_mode}")