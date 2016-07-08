/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateTableNode

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package	org.apache.dearbaby.impl.sql.compile;

import java.util.Properties;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.property.PropertyUtil;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.impl.sql.execute.ColumnInfo;
import org.apache.derby.impl.sql.execute.CreateConstraintConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateTableNode is the root of a QueryTree that represents a CREATE TABLE or DECLARE GLOBAL TEMPORARY TABLE
 * statement.
 *
 */

class CreateTableNode extends DDLStatementNode
{
	private char				lockGranularity;
	private boolean				onCommitDeleteRows; //If true, on commit delete rows else on commit preserve rows of temporary table.
	private boolean				onRollbackDeleteRows; //If true, on rollback delete rows from temp table if it was logically modified in that UOW. true is the only supported value
	private Properties			properties;
	private TableElementList	tableElementList;
	protected int	tableType; //persistent table or global temporary table
	private ResultColumnList	resultColumns;
	private ResultSetNode		queryExpression;

	/**
     * Constructor for a CreateTableNode for a base table
	 *
     * @param tableName The name of the new object being created (ie base table)
	 * @param tableElementList	The elements of the table: columns,
	 *				constraints, etc.
	 * @param properties		The optional list of properties associated with
	 *							the table.
	 * @param lockGranularity	The lock granularity.
     * @param cm                The context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    CreateTableNode(
            TableName        tableName,
            TableElementList tableElementList,
            Properties       properties,
            char             lockGranularity,
            ContextManager   cm) throws StandardException
	{
        super(tableName, cm);
        this.tableType = TableDescriptor.BASE_TABLE_TYPE;
        this.lockGranularity = lockGranularity;
        this.implicitCreateSchema = true;

		if (SanityManager.DEBUG)
		{
			if (this.lockGranularity != TableDescriptor.TABLE_LOCK_GRANULARITY &&
				this.lockGranularity != TableDescriptor.ROW_LOCK_GRANULARITY)
			{
				SanityManager.THROWASSERT(
				"Unexpected value for lockGranularity = " + this.lockGranularity);
			}
		}

        this.tableElementList = tableElementList;
        this.properties = properties;
	}

	/**
     * Constructor for a CreateTableNode for a global temporary table
	 *
     * @param tableName The name of the new object being declared (ie
     *                  temporary table)
     * @param tableElementList  The elements of the table: columns,
     *                          constraints, etc.
	 * @param properties		The optional list of properties associated with
	 *							the table.
	 * @param onCommitDeleteRows	If true, on commit delete rows else on commit preserve rows of temporary table.
	 * @param onRollbackDeleteRows	If true, on rollback, delete rows from temp tables which were logically modified. true is the only supported value
	 *
	 * @exception StandardException		Thrown on error
	 */
    CreateTableNode(
            TableName tableName,
            TableElementList tableElementList,
            Properties properties,
            boolean onCommitDeleteRows,
            boolean onRollbackDeleteRows,
            ContextManager cm)
		throws StandardException
	{
        super(tempTableSchemaNameCheck(tableName), cm);

        this.tableType = TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE;
        this.onCommitDeleteRows = onCommitDeleteRows;
        this.onRollbackDeleteRows = onRollbackDeleteRows;
        this.tableElementList = tableElementList;
        this.properties = properties;

		if (SanityManager.DEBUG)
		{
			if (this.onRollbackDeleteRows == false)
			{
				SanityManager.THROWASSERT(
				"Unexpected value for onRollbackDeleteRows = " + this.onRollbackDeleteRows);
			}
		}
	}
	
	/**
     * Constructor for a CreateTableNode for a base table create from a query
	 * 
     * @param tableName         The name of the new object being created
	 * 	                        (ie base table).
	 * @param resultColumns		The optional column list.
	 * @param queryExpression	The query expression for the table.
     * @param cm                The context manager
	 */
    CreateTableNode(
            TableName tableName,
            ResultColumnList resultColumns,
            ResultSetNode queryExpression,
            ContextManager cm) throws StandardException
	{
        super(tableName, cm);
        this.tableType = TableDescriptor.BASE_TABLE_TYPE;
        this.lockGranularity = TableDescriptor.DEFAULT_LOCK_GRANULARITY;
        this.implicitCreateSchema = true;
        this.resultColumns = resultColumns;
        this.queryExpression = queryExpression;
	}

	/**
	 * If no schema name specified for global temporary table, SESSION is the implicit schema.
	 * Otherwise, make sure the specified schema name for global temporary table is SESSION.
     *
     * @param tableName The name of the new object being declared (ie
     *        temporary table)
     */
    private static TableName tempTableSchemaNameCheck(TableName tableName)
		throws StandardException {
        if (tableName != null)
		{
            if (tableName.getSchemaName() == null) {
                // If no schema specified, SESSION is the implicit schema.
                tableName.setSchemaName(SchemaDescriptor.
                    STD_DECLARED_GLOBAL_TEMPORARY_TABLES_SCHEMA_NAME);

            } else if (!(isSessionSchema(tableName.getSchemaName()))) {
                throw StandardException.newException(SQLState.
                    LANG_DECLARED_GLOBAL_TEMP_TABLE_ONLY_IN_SESSION_SCHEMA);
            }
		}
        return tableName;
	}

	/**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */
    @Override
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			String tempString = "";
			if (tableType == TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE)
			{
				tempString = tempString + "onCommitDeleteRows: " + "\n" + onCommitDeleteRows + "\n";
				tempString = tempString + "onRollbackDeleteRows: " + "\n" + onRollbackDeleteRows + "\n";
			} else
				tempString = tempString +
					(properties != null ?
					 "properties: " + "\n" + properties + "\n" :
					 "") +
					"lockGranularity: " + lockGranularity + "\n";
			return super.toString() +  tempString;
		}
		else
		{
			return "";
		}
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 * @param depth		The depth to indent the sub-nodes
	 */
    @Override
    void printSubNodes(int depth) {
		if (SanityManager.DEBUG) {
			printLabel(depth, "tableElementList: ");
			tableElementList.treePrint(depth + 1);
		}
	}


    String statementToString()
	{
		if (tableType == TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE)
			return "DECLARE GLOBAL TEMPORARY TABLE";
		else
			return "CREATE TABLE";
	}

	// We inherit the generate() method from DDLStatementNode.
 

	/**
	 * Return true if the node references SESSION schema tables (temporary or permanent)
	 *
	 * @return	true if references SESSION schema tables, else false
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
	public boolean referencesSessionSchema()
		throws StandardException
	{
		//If table being created/declared is in SESSION schema, then return true.
		return isSessionSchema(
			getSchemaDescriptor(
				tableType != TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE,
				true));
	}

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction() throws StandardException
	{
		TableElementList		coldefs = tableElementList;

		// for each column, stuff system.column
		ColumnInfo[] colInfos = new ColumnInfo[coldefs.countNumberOfColumns()];

	    int numConstraints = coldefs.genColumnInfos(colInfos);

		/* If we've seen a constraint, then build a constraint list */
		CreateConstraintConstantAction[] conActions = null;

		SchemaDescriptor sd = getSchemaDescriptor(
			tableType != TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE,
			true);

		
		if (numConstraints > 0)
		{
			conActions =
                new CreateConstraintConstantAction[numConstraints];

			coldefs.genConstraintActions(true,
                conActions, getRelativeName(), sd, getDataDictionary());
		}

        // if the any of columns are "long" and user has not specified a
        // page size, set the pagesize to 32k.
        // Also in case where the approximate sum of the column sizes is
        // greater than the bump threshold , bump the pagesize to 32k

        boolean table_has_long_column = false;
        int approxLength = 0;

        for (int i = 0; i < colInfos.length; i++)
        {
            DataTypeDescriptor dts = colInfos[i].getDataType();
            if (dts.getTypeId().isLongConcatableTypeId())
            {
                table_has_long_column = true;
                break;
            }

            approxLength += dts.getTypeId().getApproximateLengthInBytes(dts);
        }

        if (table_has_long_column || (approxLength > Property.TBL_PAGE_SIZE_BUMP_THRESHOLD))
        {
			if (((properties == null) ||
                 (properties.get(Property.PAGE_SIZE_PARAMETER) == null)) &&
                (PropertyUtil.getServiceProperty(
                     getLanguageConnectionContext().getTransactionCompile(),
                     Property.PAGE_SIZE_PARAMETER) == null))
            {
                // do not override the user's choice of page size, whether it
                // is set for the whole database or just set on this statement.

                if (properties == null)
                    properties = new Properties();

                properties.put(
                    Property.PAGE_SIZE_PARAMETER,
                    Property.PAGE_SIZE_DEFAULT_LONG);
            }
        }

		return(
            getGenericConstantActionFactory().getCreateTableConstantAction(
                sd.getSchemaName(),
                getRelativeName(),
                tableType,
                colInfos,
                conActions,
                properties,
                lockGranularity,
                onCommitDeleteRows,
                onRollbackDeleteRows));
	}

	/**
	 * Accept the visitor for all visitable children of this node.
	 * 
	 * @param v the visitor
	 *
	 * @exception StandardException on error
	 */
    @Override
	void acceptChildren(Visitor v)
		throws StandardException
	{
		super.acceptChildren(v);

		if (tableElementList != null)
		{
			tableElementList.accept(v);
		}
	}
    
}
