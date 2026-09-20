# Report 528 — The receipt prints what was billed

## 1. Identification
- **Report number:** 528
- **Task ID:** FEAT-REAL-RECEIPT
- **Predecessor:** report 527 (other branch); on `main` the last is 523

## 2. Objective
Testing on a real printer showed the receipt had almost nothing on it. Make it a real receipt: table, date, items, subtotal/tax/total, header and footer, laid out for the paper in Settings.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/service/ReceiptRenderer.java` (rewritten)
- `backend/src/main/java/com/vanter/ember/printing/service/ReceiptLayout.java` (new)
- `backend/src/test/java/com/vanter/ember/printing/service/ReceiptRendererTest.java` (rewritten), `ReceiptLayoutTest.java` (new)
- `PROGRESS.md`, `reports/528-feat-real-receipt.md`

## 4. What Changed?
**Root cause.** The stored payload of the last Hub receipt was literally `EMBER / Bill #1 / Gracias por visitarnos`: `ReceiptRenderer.render` only wrote the header message, `Bill #<id>` and the footer. It ignored the bill, the session, the table and the ticket settings that already exist (`paperWidth`, `showTaxBreakdown`). The agent and the printer were fine — the driver-mode sender draws every line it is given.

**Now** (`ReceiptRenderer` gathers the data, `ReceiptLayout` is pure formatting):
- Header (Settings header, or the restaurant name when it is blank), `Mesa N`, `Cuenta #id` + date/time.
- The items the billing itself charges (delivered or ready), identical units grouped (`3 Cerveza … C$60.00`), each item's modifiers underneath.
- `Subtotal`, `Impuesto (15%)` and `TOTAL`. The total is the bill's own; the tax line is the difference to the subtotal, so it always agrees with what was charged. With "show tax breakdown" off (or no tax), only `TOTAL` is printed.
- Width from Settings: 32 columns for 58 mm, 42 for 80 mm; long names and labels wrap, no line exceeds the width. Plain ASCII only.
- Never fails: a missing bill or session prints a shorter receipt instead of an error.

Example (80 mm):
```
                  EMBER
Mesa 5
Cuenta #12                  20/09/26 17:09
------------------------------------------
2 Hamburguesa                       C$50.00
  + Extra queso
1 Coca Cola                          C$0.00
------------------------------------------
Subtotal                            C$50.00
Impuesto (15%)                       C$7.50
TOTAL                               C$57.50
------------------------------------------
            Gracias por visitarnos
```

## 5. Why It Changed?
A receipt with no items or total is not usable. The data model already had everything needed; only the renderer was a stub.

**Not included (by decision):** how it was paid (cash/card), and the tip — the system stores no tip on a bill or payment (only suggested percentages in Settings), so `showTip` still has nothing to print. Kitchen tickets are unchanged.

Verification: backend `./mvnw test` **1423/1423** (9 layout + 8 renderer tests). Not yet checked on paper. **Release impact:** backend change → needs a new tag, a cloud deploy and a rebuilt Hub installer to reach a restaurant; the agent is unaffected.
