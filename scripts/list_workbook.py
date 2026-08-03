import openpyxl
import os

# Find the workbook
wb_path = None
for f in os.listdir('.'):
    if f.endswith('.xlsx') and 'Royal Kingdom' in f:
        wb_path = f
        break

if not wb_path:
    # Try bigger search
    for root, dirs, files in os.walk('.'):
        for f in files:
            if f.endswith('.xlsx') and 'Royal Kingdom' in f:
                wb_path = os.path.join(root, f)
                break
        if wb_path:
            break

if not wb_path:
    print("Workbook not found, listing xlsx files:")
    for root, dirs, files in os.walk('.'):
        for f in files:
            if f.endswith('.xlsx'):
                print(os.path.join(root, f))
else:
    print(f"Found workbook: {wb_path}")
    wb = openpyxl.load_workbook(wb_path, data_only=True)
    print(f"Sheets: {wb.sheetnames}")
    
    # Find settlement-related sheets
    for sname in wb.sheetnames:
        if 'settlement' in sname.lower() or 'urban' in sname.lower():
            ws = wb[sname]
            print(f"\n=== Sheet: {sname} ({ws.max_row}x{ws.max_column}) ===")
            # Print all rows with content
            for row in ws.iter_rows(min_row=1, max_row=min(ws.max_row, 50), values_only=False):
                vals = [(cell.coordinate, cell.value) for cell in row if cell.value is not None]
                if vals:
                    print(vals)
