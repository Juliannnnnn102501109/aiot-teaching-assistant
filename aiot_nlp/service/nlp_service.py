from fastapi import FastAPI, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import os
import tempfile
import json
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.qwen_client import get_qwen_client
from core.file_parser import FileParser
from core.prompt_templates import build_intent_messages, INTENT_SCHEMA

app = FastAPI(title="AIOT Teaching Assistant NLP Engine")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

qwen_client = None

class IntentRequest(BaseModel):
    text: str
    file_content: str = None

@app.on_event("startup")
async def startup_event():
    """Initializing the model upon application startup"""
    global qwen_client
    print("Initializing Qwen model...")
    qwen_client = get_qwen_client()
    print("Model initialization completed")

@app.get("/")
async def root():
    """Root path, used for health checks."""
    return {
        "status": "running", 
        "service": "AIOT Teaching Assistant NLP Service",
        "model": "Qwen2.5-7B-Instruct"
    }

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "model_loaded": qwen_client is not None and qwen_client.llm is not None
    }

@app.post("/extract_intent")
async def extract_intent(request: IntentRequest):
    """Teaching intent understanding interface"""
    try:
        messages = build_intent_messages(request.text, request.file_content)
        
        result_text = qwen_client.generate(messages, use_json_schema=INTENT_SCHEMA)
        
        try:
            if "```json" in result_text:
                result_text = result_text.split("```json")[1].split("```")[0].strip()
            elif "```" in result_text:
                result_text = result_text.split("```")[1].strip()
                
            intent_data = json.loads(result_text)
        except json.JSONDecodeError as e:
            print(f"JSON parsing failed: {e}")
            print(f"Raw output: {result_text}")
            intent_data = {
                "goal": "Parsing failed, please retry",
                "key_points": [],
                "difficult_points": [],
                "style": "图文并茂",
                "grade_level": "初中",
                "time_requirement": 45,
                "raw_output": result_text[:500]
            }
        
        return {
            "success": True,
            "intent": intent_data,
            "model_used": "Qwen2.5-7B-Instruct"
        }
        
    except Exception as e:
        return {
            "success": False,
            "error": str(e),
            "intent": None
        }

@app.post("/upload_file")
async def upload_file(file: UploadFile = File(...)):
    """File upload parsing interface"""
    try:
        file_ext = os.path.splitext(file.filename)[-1].lower()
        with tempfile.NamedTemporaryFile(delete=False, suffix=file_ext) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name
        
        text_content = ""
        if file_ext == '.pdf':
            text_content = FileParser.parse_pdf(tmp_path)
        elif file_ext in ['.docx', '.doc']:
            text_content = FileParser.parse_word(tmp_path)
        elif file_ext == '.txt':
            text_content = FileParser.parse_txt(tmp_path)
        else:
            text_content = f"Unsupported file format: {file_ext}"
  
        os.unlink(tmp_path)
        
        if len(text_content) > 10000:
            text_content = text_content[:10000] + "\n...[Content too long, truncated]..."
        
        return {
            "success": True,
            "filename": file.filename,
            "file_type": file_ext,
            "content": text_content,
            "content_length": len(text_content)
        }
        
    except Exception as e:
        return {
            "success": False,
            "filename": file.filename,
            "error": str(e)
        }

@app.post("/chat")
async def chat_endpoint(message: str):
    """Simple chat interface for model testing"""
    try:
        messages = [
            {"role": "system", "content": "你是一个专业的教学助手。"},
            {"role": "user", "content": message}
        ]
        
        response = qwen_client.generate(messages)
        
        return {
            "success": True,
            "response": response,
            "model": "Qwen2.5-7B-Instruct"
        }
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

if __name__ == "__main__":
    import uvicorn
    print("Starting AIOT NLP service...")
    print("API documentation: http://localhost:8000/docs")
    print("Health check: http://localhost:8000/health")
    uvicorn.run(app, host="0.0.0.0", port=8000)
