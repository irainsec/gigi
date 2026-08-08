
import re

def check_top_level(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    stack = []
    errors = []
    
    for i, line in enumerate(lines):
        # Very simple heuristic for top-level declarations
        if line.startswith('fun ') or line.startswith('class ') or line.startswith('interface ') or line.startswith('@Composable\nfun '):
             pass # just noting
        
        for char in line:
            if char == '{':
                stack.append(i + 1)
            elif char == '}':
                if not stack:
                    errors.append(f"Extra closing brace at line {i + 1}")
                else:
                    stack.pop()
    
    if stack:
        for line_num in stack:
            errors.append(f"Unclosed opening brace at line {line_num}")
            
    for error in errors:
        print(error)

check_top_level(r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt')
