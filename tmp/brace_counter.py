
def count_braces(file_path, start_line, end_line):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    subset = lines[start_line-1 : end_line]
    open_count = 0
    close_count = 0
    for line in subset:
        open_count += line.count('{')
        close_count += line.count('}')
    
    print(f"Lines {start_line}-{end_line}:")
    print(f"Open Braces: {open_count}")
    print(f"Close Braces: {close_count}")
    print(f"Balance: {open_count - close_count}")

count_braces(r'c:\Users\ATPL-ADMIN\Downloads\gigi\app\src\main\java\com\aman\gigi\ui\LoveCardsSection.kt', 690, 950)
