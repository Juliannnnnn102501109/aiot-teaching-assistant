# /home/no102501109/aiot_nlp/core/qwen_client.py
import os
import json
from openai import OpenAI
from vllm import LLM, SamplingParams
from transformers import AutoTokenizer

class QwenClient:
    def __init__(self, model_path="./models/Qwen2.5-7B-Instruct"):
        try:
            print(f"Loading model: {model_path}")
            print("This process may take a few minutes, please be patient...")
            
            if not os.path.exists(model_path):
                print(f"Model path does not exist: {model_path}")
                print("Attempting to load from the default path...")
                from transformers.utils.hub import get_cache_dir
                cache_dir = get_cache_dir()
                model_path = os.path.join(cache_dir, "models--Qwen--Qwen2.5-7B-Instruct")
                print(f"Cache path: {model_path}")
            
            import torch
            gpu_memory = torch.cuda.get_device_properties(0).total_memory / 1e9  
            
            if gpu_memory < 12:  
                print(f"Low VRAM detected: {gpu_memory:.1f}GB，enabling 8-bit quantization")
                self.llm = LLM(
                    model=model_path,
                    tensor_parallel_size=1,
                    gpu_memory_utilization=0.8,
                    max_model_len=4096,
                    trust_remote_code=True,
                    quantization="awq",  
                    dtype="auto",
                    enable_prefix_caching=True
                )
            elif gpu_memory < 24:  
                print(f"Moderate VRAM detected: {gpu_memory:.1f}GB，using half-precision")
                self.llm = LLM(
                    model=model_path,
                    tensor_parallel_size=1,
                    gpu_memory_utilization=0.85,
                    max_model_len=8192,
                    trust_remote_code=True,
                    dtype="half",  
                    enable_prefix_caching=True
                )
            else:  
                print(f"High VRAM detected: {gpu_memory:.1f}GB，using full precision.")
                self.llm = LLM(
                    model=model_path,
                    tensor_parallel_size=1,
                    gpu_memory_utilization=0.9,
                    max_model_len=32768,
                    trust_remote_code=True,
                    enable_prefix_caching=True
                )
            
            self.tokenizer = AutoTokenizer.from_pretrained(
                model_path,
                trust_remote_code=True
            )
            
            self.sampling_params = SamplingParams(
                temperature=0.1,
                top_p=0.9,
                max_tokens=2048,
                stop=["<|im_end|>"]
            )
            
            print("Model loading completed!")
            
        except ImportError as e:
            print(f"Import error: {e}")
            print("Please ensure vLLM is installed: pip install vllm")
            raise
        except Exception as e:
            print(f"Model loading failed: {e}")
            print("Falling back to simulation mode (for testing only)")
            self.llm = None
            self.tokenizer = None
            self.sampling_params = None
    
    def generate(self, messages, use_json_schema=None):
        if self.llm is None:
            print("Warning: Using simulation mode returns test data")
            if use_json_schema:
                return json.dumps({
                    "goal": "理解勾股定理的定义和证明",
                    "key_points": ["直角三角形定义", "勾股定理公式"],
                    "difficult_points": ["无理数的概念"],
                    "style": "图文并茂",
                    "grade_level": "初中",
                    "time_requirement": 45
                })
            return "This is a test reply; the actual model is not loaded."
        
        try:
            if self.tokenizer is not None and hasattr(self.tokenizer, 'apply_chat_template'):
                prompt = self.tokenizer.apply_chat_template(
                    messages,
                    tokenize=False,
                    add_generation_prompt=True
                )
            else:
                prompt = ""
                for msg in messages:
                    role = msg["role"]
                    content = msg["content"]
                    if role == "system":
                        prompt += f"<|im_start|>system\n{content}<|im_end|>\n"
                    elif role == "user":
                        prompt += f"<|im_start|>user\n{content}<|im_end|>\n"
                    elif role == "assistant":
                        prompt += f"<|im_start|>assistant\n{content}<|im_end|>\n"
                prompt += "<|im_start|>assistant\n"
            
            if use_json_schema:
                for i, msg in enumerate(messages):
                    if msg["role"] == "system":
                        messages[i]["content"] += f"\n\nYou must strictly adhere to the following JSON format for output：\n{json.dumps(use_json_schema, ensure_ascii=False)}"
                        break
            
            outputs = self.llm.generate([prompt], self.sampling_params)
            generated_text = outputs[0].outputs[0].text
            
            return generated_text.strip()
            
        except Exception as e:
            print(f"Error occurred during generation: {e}")
            return f"Generation failed: {str(e)}"

qwen_client_instance = None

def get_qwen_client():
    global qwen_client_instance
    if qwen_client_instance is None:
        qwen_client_instance = QwenClient()
    return qwen_client_instance
