/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.excelinput.ods;

import org.apache.hop.core.spreadsheet.IKCell;
import org.apache.hop.core.spreadsheet.IKSheet;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.odftoolkit.odfdom.dom.element.table.TableTableCellElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableRowElement;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OdfSheet implements IKSheet {
  private OdfTable table;
  private int nrOfRows;
  private int roughNrOfCols;

  public OdfSheet(OdfTable table) {
    this.table = table;
    nrOfRows = findNrRows();
    roughNrOfCols = table.getColumnCount();
  }

  /**
   * Calculate the number of rows in the table
   *
   * @return number of rows in the table
   */
  protected int findNrRows() {

    int rowCount = table.getRowCount();

    // remove last empty rows from counter
    NodeList nodes = table.getOdfElement().getChildNodes();
    int nodesLen = nodes.getLength();
    for (int i = nodesLen - 1; i >= 0; i--) {
      Node node = nodes.item(i);
      if (node instanceof TableTableRowElement rowElement) {
        if (isRowEmpty(rowElement)) {
          // remove this row from counter
          rowCount -= rowElement.getTableNumberRowsRepeatedAttribute();
        } else {
          // stop checking at first non-empty row
          break;
        }
      }
    }

    return rowCount;
  }

  /**
   * Check if row contains non-empty cells
   *
   * @param rowElem
   * @return
   */
  protected boolean isRowEmpty(TableTableRowElement rowElem) {
    NodeList cells = rowElem.getChildNodes();
    int cellsLen = cells.getLength();
    for (int j = 0; j < cellsLen; j++) { // iterate over cells
      Node cell = cells.item(j);
      if (cell instanceof TableTableCellElement) {
        if (cell.hasChildNodes()) {
          return false;
        }
      }
    }
    return true;
  }

  protected int findNrColumns(OdfTableRow row) {
    int result = roughNrOfCols;
    if (row != null) {
      NodeList cells = row.getOdfElement().getChildNodes();
      if (cells != null && cells.getLength() > 0) {
        int cellLen = cells.getLength();
        for (int i = cellLen - 1; i >= 0; i--) {
          Node cell = cells.item(i);
          if (cell instanceof TableTableCellElement tableCellElement) {
            if (!cell.hasChildNodes()) {
              // last cell is empty - remove it from counter
              result -= tableCellElement.getTableNumberColumnsRepeatedAttribute();
            } else {
              // get first non-empty cell from the end, break
              break;
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public String getName() {
    return table.getTableName();
  }

  @Override
  public IKCell[] getRow(int rownr) {
    if (rownr >= nrOfRows) {
      throw new ArrayIndexOutOfBoundsException("Read beyond last row: " + rownr);
    }
    OdfTableRow row = table.getRowByIndex(rownr);
    int cols = findNrColumns(row);
    OdfCell[] xlsCells = new OdfCell[cols];
    for (int i = 0; i < cols; i++) {
      OdfTableCell cell = row.getCellByIndex(i);
      if (cell != null) {
        xlsCells[i] = new OdfCell(cell);
      }
    }
    return xlsCells;
  }

  @Override
  public int getRows() {
    return nrOfRows;
  }

  @Override
  public IKCell getCell(int colnr, int rownr) {
    OdfTableCell cell = table.getCellByPosition(colnr, rownr);
    if (cell == null) {
      return null;
    }
    return new OdfCell(cell);
  }
}
