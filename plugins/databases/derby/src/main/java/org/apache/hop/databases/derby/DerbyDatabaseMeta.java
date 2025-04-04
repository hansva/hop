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

package org.apache.hop.databases.derby;

import org.apache.hop.core.Const;
import org.apache.hop.core.database.BaseDatabaseMeta;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.database.DatabaseMetaPlugin;
import org.apache.hop.core.database.IDatabase;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;

/** Contains Apache Derby Database Connection information through static final members */
@DatabaseMetaPlugin(
    type = "DERBY",
    typeDescription = "Apache Derby",
    documentationUrl = "/database/databases/derby.html")
@GuiPlugin(id = "GUI-DerbyDatabaseMeta")
public class DerbyDatabaseMeta extends BaseDatabaseMeta implements IDatabase {
  @Override
  public int[] getAccessTypeList() {
    return new int[] {DatabaseMeta.TYPE_ACCESS_NATIVE};
  }

  /**
   * @see IDatabase#getNotFoundTK(boolean)
   */
  @Override
  public int getNotFoundTK(boolean useAutoinc) {
    if (isSupportsAutoInc() && useAutoinc) {
      return 0;
    }
    return super.getNotFoundTK(useAutoinc);
  }

  @Override
  public String getDriverClass() {
    if (Utils.isEmpty(getHostname())) {
      return "org.apache.derby.jdbc.EmbeddedDriver";
    } else {
      return "org.apache.derby.client.ClientAutoloadedDriver";
    }
  }

  @Override
  public String getURL(String hostname, String port, String databaseName) {
    if (!Utils.isEmpty(hostname)) {
      String url = "jdbc:derby://" + hostname;
      if (!Utils.isEmpty(port)) {
        url += ":" + port;
      }
      url += "/" + databaseName;
      return url;
    } else { // Simple format: jdbc:derby:<dbname>
      return "jdbc:derby:" + databaseName;
    }
  }

  /**
   * Checks whether or not the command setFetchSize() is supported by the JDBC driver...
   *
   * @return true is setFetchSize() is supported!
   */
  @Override
  public boolean isFetchSizeSupported() {
    return true;
  }

  /**
   * @return true if the database supports bitmap indexes
   */
  @Override
  public boolean isSupportsBitmapIndex() {
    return false;
  }

  /**
   * @param tableName The table to be truncated.
   * @return The SQL statement to truncate a table: remove all rows from it without a transaction
   */
  @Override
  public String getTruncateTableStatement(String tableName) {
    return "DELETE FROM " + tableName;
  }

  /**
   * Generates the SQL statement to add a column to the specified table For this generic type, i set
   * it to the most common possibility.
   *
   * @param tableName The table to add
   * @param v The column defined as a value
   * @param tk the name of the technical key field
   * @param useAutoinc whether or not this field uses auto increment
   * @param pk the name of the primary key field
   * @param semicolon whether or not to add a semi-colon behind the statement.
   * @return the SQL statement to add a column to the specified table
   */
  @Override
  public String getAddColumnStatement(
      String tableName, IValueMeta v, String tk, boolean useAutoinc, String pk, boolean semicolon) {
    return "ALTER TABLE "
        + tableName
        + " ADD "
        + getFieldDefinition(v, tk, pk, useAutoinc, true, false);
  }

  /**
   * Generates the SQL statement to modify a column in the specified table
   *
   * @param tableName The table to add
   * @param v The column defined as a value
   * @param tk the name of the technical key field
   * @param useAutoinc whether or not this field uses auto increment
   * @param pk the name of the primary key field
   * @param semicolon whether or not to add a semi-colon behind the statement.
   * @return the SQL statement to modify a column in the specified table
   */
  @Override
  public String getModifyColumnStatement(
      String tableName, IValueMeta v, String tk, boolean useAutoinc, String pk, boolean semicolon) {
    return "ALTER TABLE "
        + tableName
        + " ALTER "
        + getFieldDefinition(v, tk, pk, useAutoinc, true, false);
  }

  @Override
  public String getFieldDefinition(
      IValueMeta v, String tk, String pk, boolean useAutoinc, boolean addFieldName, boolean addCr) {
    String retval = "";

    String fieldname = v.getName();
    int length = v.getLength();
    int precision = v.getPrecision();

    if (addFieldName) {
      retval += fieldname + " ";
    }

    int type = v.getType();
    switch (type) {
      case IValueMeta.TYPE_TIMESTAMP, IValueMeta.TYPE_DATE:
        retval += "TIMESTAMP";
        break;
      case IValueMeta.TYPE_BOOLEAN:
        retval += "CHAR(1)";
        break;
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_INTEGER, IValueMeta.TYPE_BIGNUMBER:
        if (fieldname.equalsIgnoreCase(tk)
            || // Technical key
            fieldname.equalsIgnoreCase(pk) // Primary key
        ) {
          if (useAutoinc) {
            retval += "BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 0, INCREMENT BY 1)";
          } else {
            retval += "BIGINT NOT NULL PRIMARY KEY";
          }
        } else {
          if (type == IValueMeta.TYPE_INTEGER) {
            // Integer values...
            if (length < 3) {
              retval += "TINYINT";
            } else if (length < 5) {
              retval += "SMALLINT";
            } else if (length < 10) {
              retval += "INT";
            } else if (length < 20) {
              retval += "BIGINT";
            } else {
              retval += "DECIMAL(" + length + ")";
            }
          } else if (type == IValueMeta.TYPE_BIGNUMBER) {
            // Fixed point value...
            if (length
                < 1) { // user configured no value for length. Use 16 digits, which is comparable to
              // mantissa 2^53 of IEEE 754 binary64 "double".
              length = 16;
            }
            if (precision
                < 1) { // user configured no value for precision. Use 16 digits, which is comparable
              // to IEEE 754 binary64 "double".
              precision = 16;
            }
            retval += "DECIMAL(" + length + "," + precision + ")";
          } else {
            // Floating point value with double precision...
            retval += "DOUBLE";
          }
        }
        break;
      case IValueMeta.TYPE_STRING:
        if (length >= DatabaseMeta.CLOB_LENGTH || length > 32700) {
          retval += "CLOB";
        } else {
          retval += "VARCHAR";
          if (length > 0) {
            retval += "(" + length;
          } else {
            retval += "("; // Maybe use some default DB String length?
          }
          retval += ")";
        }
        break;
      case IValueMeta.TYPE_BINARY:
        retval += "BLOB";
        break;
      default:
        retval += "UNKNOWN";
        break;
    }

    if (addCr) {
      retval += Const.CR;
    }

    return retval;
  }

  @Override
  public int getDefaultDatabasePort() {
    return 1527;
  }

  @Override
  public boolean isSupportsGetBlob() {
    return false;
  }

  @Override
  public String getExtraOptionsHelpText() {
    return "http://db.apache.org/derby/papers/DerbyClientSpec.html";
  }

  @Override
  public String[] getReservedWords() {
    return new String[] {
      "ADD",
      "ALL",
      "ALLOCATE",
      "ALTER",
      "AND",
      "ANY",
      "ARE",
      "AS",
      "ASC",
      "ASSERTION",
      "AT",
      "AUTHORIZATION",
      "AVG",
      "BEGIN",
      "BETWEEN",
      "BIT",
      "BOOLEAN",
      "BOTH",
      "BY",
      "CALL",
      "CASCADE",
      "CASCADED",
      "CASE",
      "CAST",
      "CHAR",
      "CHARACTER",
      "CHECK",
      "CLOSE",
      "COLLATE",
      "COLLATION",
      "COLUMN",
      "COMMIT",
      "CONNECT",
      "CONNECTION",
      "CONSTRAINT",
      "CONSTRAINTS",
      "CONTINUE",
      "CONVERT",
      "CORRESPONDING",
      "COUNT",
      "CREATE",
      "CURRENT",
      "CURRENT_DATE",
      "CURRENT_TIME",
      "CURRENT_TIMESTAMP",
      "CURRENT_USER",
      "CURSOR",
      "DEALLOCATE",
      "DEC",
      "DECIMAL",
      "DECLARE",
      "DEFERRABLE",
      "DEFERRED",
      "DELETE",
      "DESC",
      "DESCRIBE",
      "DIAGNOSTICS",
      "DISCONNECT",
      "DISTINCT",
      "DOUBLE",
      "DROP",
      "ELSE",
      "END",
      "ENDEXEC",
      "ESCAPE",
      "EXCEPT",
      "EXCEPTION",
      "EXEC",
      "EXECUTE",
      "EXISTS",
      "EXPLAIN",
      "EXTERNAL",
      "FALSE",
      "FETCH",
      "FIRST",
      "FLOAT",
      "FOR",
      "FOREIGN",
      "FOUND",
      "FROM",
      "FULL",
      "FUNCTION",
      "GET",
      "GET_CURRENT_CONNECTION",
      "GLOBAL",
      "GO",
      "GOTO",
      "GRANT",
      "GROUP",
      "HAVING",
      "HOUR",
      "IDENTITY",
      "IMMEDIATE",
      "IN",
      "INDICATOR",
      "INITIALLY",
      "INNER",
      "INOUT",
      "INPUT",
      "INSENSITIVE",
      "INSERT",
      "INT",
      "INTEGER",
      "INTERSECT",
      "INTO",
      "IS",
      "ISOLATION",
      "JOIN",
      "KEY",
      "LAST",
      "LEFT",
      "LIKE",
      "LONGINT",
      "LOWER",
      "LTRIM",
      "MATCH",
      "MAX",
      "MIN",
      "MINUTE",
      "NATIONAL",
      "NATURAL",
      "NCHAR",
      "NVARCHAR",
      "NEXT",
      "NO",
      "NOT",
      "NULL",
      "NULLIF",
      "NUMERIC",
      "OF",
      "ON",
      "ONLY",
      "OPEN",
      "OPTION",
      "OR",
      "ORDER",
      "OUT",
      "OUTER",
      "OUTPUT",
      "OVERLAPS",
      "PAD",
      "PARTIAL",
      "PREPARE",
      "PRESERVE",
      "PRIMARY",
      "PRIOR",
      "PRIVILEGES",
      "PROCEDURE",
      "PUBLIC",
      "READ",
      "REAL",
      "REFERENCES",
      "RELATIVE",
      "RESTRICT",
      "REVOKE",
      "RIGHT",
      "ROLLBACK",
      "ROWS",
      "RTRIM",
      "SCHEMA",
      "SCROLL",
      "SECOND",
      "SELECT",
      "SESSION_USER",
      "SET",
      "SMALLINT",
      "SOME",
      "SPACE",
      "SQL",
      "SQLCODE",
      "SQLERROR",
      "SQLSTATE",
      "SUBSTR",
      "SUBSTRING",
      "SUM",
      "SYSTEM_USER",
      "TABLE",
      "TEMPORARY",
      "TIMEZONE_HOUR",
      "TIMEZONE_MINUTE",
      "TO",
      "TRAILING",
      "TRANSACTION",
      "TRANSLATE",
      "TRANSLATION",
      "TRUE",
      "UNION",
      "UNIQUE",
      "UNKNOWN",
      "UPDATE",
      "UPPER",
      "USER",
      "USING",
      "VALUES",
      "VARCHAR",
      "VARYING",
      "VIEW",
      "WHENEVER",
      "WHERE",
      "WITH",
      "WORK",
      "WRITE",
      "XML",
      "XMLEXISTS",
      "XMLPARSE",
      "XMLSERIALIZE",
      "YEAR"
    };
  }

  /**
   * Get the SQL to insert a new empty unknown record in a dimension.
   *
   * @param schemaTable the schema-table name to insert into
   * @param keyField The key field
   * @param versionField the version field
   * @return the SQL to insert the unknown record into the SCD.
   */
  @Override
  public String getSqlInsertAutoIncUnknownDimensionRow(
      String schemaTable, String keyField, String versionField) {
    return "insert into " + schemaTable + "(" + versionField + ") values (1)";
  }
}
