import os
import fitz  
import pdfplumber
from docx import Document

class FileParser:
    @staticmethod
    def parse_pdf(file_path):
        text = ""
        try:
            with pdfplumber.open(file_path) as pdf:
                for page in pdf.pages:
                    page_text = page.extract_text()
                    if page_text:
                        text += page_text + "\n"
        except Exception as e1:
            print(f"pdfplumber failed: {e1}")
            try:
                doc = fitz.open(file_path)
                for page in doc:
                    text += page.get_text()
                doc.close()
            except Exception as e2:
                print(f"PyMuPDF failed: {e2}")
                text = f"[PDF parsing failed] {str(e2)}"
        
        return text
    
    @staticmethod
    def parse_word(file_path):
        try:
            doc = Document(file_path)
            full_text = []
            for para in doc.paragraphs:
                full_text.append(para.text)
            return '\n'.join(full_text)
        except Exception as e:
            return f"[Word parsing failed] {str(e)}"
    
    @staticmethod
    def parse_txt(file_path):
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                return f.read()
        except Exception as e:
            return f"[Text parsing failed] {str(e)}"
