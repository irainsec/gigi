
def count_braces(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    open_count = content.count('{')
    close_count = content.count('}')
    
    print(f"Entire File:")
    print(f"Open Braces: {open_count}")
    print(f"Close Braces: {close_count}")
    print(f"Balance: {open_count - close_count}")

count_braces(r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt')
