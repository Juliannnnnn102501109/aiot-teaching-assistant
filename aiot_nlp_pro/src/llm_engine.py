"""
LLM Engine - 统一的大语言模型接口
支持本地 Ollama、云端 API 以及智能 Mock 降级模式
"""

import os
import sqlite3
import json
import time
from typing import Optional, Dict, Any, List
from pydantic import BaseModel, Field
from langchain_ollama import ChatOllama
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate

def init_teacher_db():
    """初始化教师行为数据库"""
    conn = sqlite3.connect('teacher_profiles.db')
    cursor = conn.cursor()
    
    # 创建表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS teacher_profiles (
            teacher_id TEXT PRIMARY KEY,
            style_profile TEXT NOT NULL
        )
    ''')
    
    conn.commit()
    conn.close()

# 初始化数据库
init_teacher_db()

TEACHING_STRATEGY_DB = {
    "生动点": [
        "您希望加入情境创设、历史典故还是生活案例？",
        "需要加入历史背景、生活场景还是趣味故事？"
    ],
    "互动性强": [
        "您希望设计小组讨论、课堂投票还是角色扮演？",
        "需要设计互动环节的时长和形式吗？"
    ],
    "时间紧张": [
        "您希望重点讲解核心概念，还是包含更多案例？",
        "需要简化哪些内容以适应时间限制？"
    ],
    "需要动画": [
        "您希望展示函数变换、物理过程还是化学反应？",
        "需要详细说明动画的交互方式吗？"
    ]
}

class TeacherBehaviorTracker:
    """教师行为跟踪器，记录教师对生成内容的修改行为"""
    def __init__(self, teacher_id: str):
        self.teacher_id = teacher_id
        self.behavior_log = []
        self.style_profile = {"case_driven": 0, "logical": 0, "interactive": 0}
        self._load_profile()
    
    def record_change(self, change_type: str, details: str):
        """记录教师修改行为"""
        self.behavior_log.append({
            "type": change_type,
            "details": details,
            "timestamp": time.time()
        })
        self._update_style_profile(change_type)
        self._save_profile()
    
    def _update_style_profile(self, change_type: str):
        """根据修改类型更新教学风格画像"""
        if change_type == "delete_intro":
            self.style_profile["logical"] += 1
        elif change_type == "reorder_activities":
            self.style_profile["interactive"] += 1
        elif change_type == "add_case":
            self.style_profile["case_driven"] += 1
    
    def get_style_profile(self) -> Dict:
        """获取教学风格画像"""
        total = sum(self.style_profile.values())
        if total > 0:
            return {k: v/total for k, v in self.style_profile.items()}
        return self.style_profile
    
    def _load_profile(self):
        """从数据库加载教师风格画像（实际部署时实现）"""
        conn = sqlite3.connect('teacher_profiles.db')
        cursor = conn.cursor()
        
        cursor.execute("""
            SELECT style_profile 
            FROM teacher_profiles 
            WHERE teacher_id = ?
        """, (self.teacher_id,))
        
        result = cursor.fetchone()
        if result:
            self.style_profile = json.loads(result[0])
        
        conn.close()
    
    def _save_profile(self):
        """保存教师风格画像到数据库（实际部署时实现）"""
        conn = sqlite3.connect('teacher_profiles.db')
        cursor = conn.cursor()
        
        profile_json = json.dumps(self.style_profile)
        
        cursor.execute("""
            SELECT COUNT(*) FROM teacher_profiles 
            WHERE teacher_id = ?
        """, (self.teacher_id,))
        
        exists = cursor.fetchone()[0]
        
        if exists:
            cursor.execute("""
                UPDATE teacher_profiles 
                SET style_profile = ?
                WHERE teacher_id = ?
            """, (profile_json, self.teacher_id))
        else:
            cursor.execute("""
                INSERT INTO teacher_profiles (teacher_id, style_profile)
                VALUES (?, ?)
            """, (self.teacher_id, profile_json))
        
        conn.commit()
        conn.close()

class TeachingElementSchema(BaseModel):
    """教学要素结构化输出格式"""
    knowledge_points: List[str] = Field(..., description="知识点清单")
    teaching_goals: List[Dict[str, str]] = Field(
        ..., 
        description="教学目标，包含布鲁姆分类法层级（如'理解'、'应用'）"
    )
    key_points: List[str] = Field(..., description="重点难点")
    common_misconceptions: List[str] = Field(..., description="学生常见误区")
    teaching_activities: List[str] = Field(..., description="所需教学活动")

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
        
        if self._needs_clarification(prompt):
            clarification_questions = self._get_clarification_questions(prompt)
            return self._format_clarification_response(clarification_questions)
        
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

    def _needs_clarification(self, prompt: str) -> bool:
        """检查是否需要澄清教学策略"""
        for key in TEACHING_STRATEGY_DB:
            if key in prompt.lower():
                return True
        return False

    def _get_clarification_questions(self, prompt: str) -> List[str]:
        """获取相关的澄清问题"""
        questions = []
        for key in TEACHING_STRATEGY_DB:
            if key in prompt.lower():
                questions.extend(TEACHING_STRATEGY_DB[key])
        return questions[:2]  # 返回最多2个相关问题

    def _format_clarification_response(self, questions: List[str]) -> str:
        """格式化澄清响应"""
        return f"为了更好地满足您的需求，我需要进一步确认：\n\n" + "\n".join(
            [f"- {q}" for q in questions]
        )  
      
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
        if system_prompt is None:
            system_prompt = (
                "你是一个专业的教学设计师，专注于帮助教师构建高质量的教学内容。"
                "请根据用户的教学需求，结构化输出以下要素，确保包含所有要求字段：\n\n"
                "1. 知识点清单（knowledge_points）\n"
                "2. 教学目标（teaching_goals），需包含布鲁姆分类法的层级（如'理解'、'应用'）\n"
                "3. 重点难点（key_points）\n"
                "4. 学生常见误区（common_misconceptions）\n"
                "5. 所需教学活动（teaching_activities）\n\n"
                "请仅输出标准JSON格式，不要包含其他内容。"
            )
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
    
    def get_teaching_elements(self, prompt: str, source_id: Optional[str] = None) -> Dict:
        """获取结构化教学要素"""
        # 生成结构化输出
        json_output = self.generate_json(
            prompt=prompt,
            system_prompt="你是一个专业的教学设计师，请结构化输出教学要素。"
        )
        
        # 如果有source_id，关联素材
        if source_id:
            # 从RAG获取关联内容
            rag_docs = self.rag_engine.get_relevant_documents(prompt, source_id)
            json_output["related_content"] = [doc.page_content for doc in rag_docs]
        
        return json_output
    
    def generate_with_style(self, prompt: str, system_prompt: Optional[str] = None, teacher_id: Optional[str] = None) -> str:
        """生成内容，根据教师风格画像调整生成策略"""
        if teacher_id:
            tracker = TeacherBehaviorTracker(teacher_id)
            style_profile = tracker.get_style_profile()

            style_prompt = ""
            if style_profile.get("case_driven", 0) > 0.5:
                style_prompt += "您偏好案例驱动的教学方式，请多加入实际案例。"
            if style_profile.get("interactive", 0) > 0.5:
                style_prompt += "您偏好互动性强的教学方式，请多设计互动环节。"
            if style_profile.get("logical", 0) > 0.5:
                style_prompt += "您偏好逻辑严密的教学方式，请确保内容结构清晰。"
            
            if system_prompt:
                system_prompt += f"\n{style_prompt}"
            else:
                system_prompt = style_prompt
        
        return self.generate(prompt, system_prompt)
    
    def record_teacher_behavior(self, teacher_id: str, change_type: str, details: str):
        """记录教师行为"""
        tracker = TeacherBehaviorTracker(teacher_id)
        tracker.record_change(change_type, details)

    def _extract_json_from_text(self, text: str) -> Optional[Dict]:
        """从文本中提取 JSON 对象"""
        if not text:
            return None
        
   
        text = text.replace("```json", "").replace("```", "").strip()

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
        "model_name": "qwen2.5:latest", 
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