import json

INTENT_SCHEMA = {
    "type": "object",
    "properties": {
        "goal": {"type": "string", "description": "明确的教学目标"},
        "key_points": {"type": "array", "items": {"type": "string"}, "description": "核心知识点列表"},
        "difficult_points": {"type": "array", "items": {"type": "string"}, "description": "教学难点"},
        "style": {"type": "string", "enum": ["图文并茂", "严谨推导", "趣味互动", "习题精讲"], "description": "课件风格"},
        "grade_level": {"type": "string", "enum": ["小学", "初中", "高中", "大学"], "description": "学段"},
        "time_requirement": {"type": "integer", "description": "预计课时（分钟）"}
    },
    "required": ["goal", "key_points", "style", "grade_level"]
}

INTENT_SYSTEM_PROMPT = """你是一个专业的教学分析师。请根据用户的输入，精确分析其教学意图，并输出JSON格式的分析结果。"""

def build_intent_messages(user_input, file_content=None):
    content = user_input
    if file_content:
        content += f"\n\n【上传的文件内容摘要】:\n{file_content[:2000]}..."  
    
    messages = [
        {"role": "system", "content": INTENT_SYSTEM_PROMPT},
        {"role": "user", "content": content}
    ]
    return messages

def build_content_generation_messages(intent_json, retrieved_content):
    system_prompt = """你是一个优秀的课件内容设计师。根据教学意图和检索到的内容，为每一页幻灯片设计内容。
输出格式：
[
  {
    "page_number": 1,
    "title": "标题",
    "content": ["要点1", "要点2"],
    "suggested_image": "建议的图片描述"
  }
]"""
    
    user_content = f"""教学意图：
{json.dumps(intent_json, ensure_ascii=False, indent=2)}

检索到的相关材料：
{retrieved_content[:3000]}

请设计一个完整的课件内容，包含封面、教学目标、知识点讲解、例题、总结等部分。"""
    
    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_content}
    ]
