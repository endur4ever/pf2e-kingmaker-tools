import openpyxl
import os

wb_path = 'Copy of Royal Kingdom Sheet for Pathfinder 2E - CURRENT.xlsx'
wb = openpyxl.load_workbook(wb_path, data_only=False)  # data_only=False to get formulas

# Urban Grids sheet - get all formulas
ws = wb['Urban Grids']
print(f"=== Urban Grids sheet ({ws.max_row}x{ws.max_column}) - ALL CELLS WITH VALUES/FORMULAS ===")
for row in ws.iter_rows(min_row=1, max_row=ws.max_row, values_only=False):
    for cell in row:
        if cell.value is not None:
            print(f"  {cell.coordinate}: {cell.value}")

print("\n\n=== Settlements sheet - ALL CELLS WITH VALUES/FORMULAS ===")
ws2 = wb['Settlements']
for row in ws2.iter_rows(min_row=1, max_row=min(ws2.max_row, 50), values_only=False):
    for cell in row:
        if cell.value is not None:
            print(f"  {cell.coordinate}: {cell.value}")
