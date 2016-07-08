/*

   Derby - Class org.apache.derby.impl.sql.compile.CurrentOfNode

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
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.compile.CostEstimate;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ExecCursorTableReference;
import org.apache.derby.iapi.sql.execute.ExecPreparedStatement;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.util.JBitSet;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * The CurrentOf operator is used by positioned DELETE 
 * and UPDATE to get the current row and location
 * for the target cursor.  The bind() operations for 
 * positioned DELETE and UPDATE add a column to 
 * the select list under the statement for the row location 
 * accessible from this node.
 *
 * This node is placed in the from clause of the select
 * generated for the delete or update operation. It acts
 * much like a FromBaseTable, using the information about
 * the target table of the cursor to provide information.
 *
 */
public final class CurrentOfNode extends FromTable {

	private String	 				cursorName;
	private ExecPreparedStatement	 preStmt;
	private TableName 				exposedTableName;
	private TableName 				baseTableName;
	private CostEstimate 			singleScanCostEstimate;

    // dummy variables for compiling a CurrentOfNode in the DELETE action of a MERGE statement
    private FromBaseTable       dummyTargetTable;

	//
	// initializers
	//
    CurrentOfNode(String correlationName,
                  String cursor,
                  Properties tableProperties,
                  ContextManager cm)
	{
        super(correlationName, tableProperties, cm);
        cursorName = cursor;
	}

    /**
     * <p>
     * Construct a dummy CurrentOfNode just for compiling the DELETE action of a MERGE
     * statement.
     * </p>
     */
    static  CurrentOfNode   makeForMerge
        (
         String cursorName,
         FromBaseTable  dummyTargetTable,
         ContextManager cm
         )
    {
        CurrentOfNode   node = new CurrentOfNode( null, cursorName, null, cm );
        node.dummyTargetTable = dummyTargetTable;

        return node;
    }

	 
	//
	// FromTable interface
	//

	/**
	 * Binding this FromTable means finding the prepared statement
	 * for the cursor and creating the result columns (the columns
	 * updatable on that cursor).
	 * 
	 * We expect someone else to verify that the target table
	 * of the positioned update or delete is the table under this cursor.
	 *
	 * @param dataDictionary	The DataDictionary to use for binding
	 * @param fromListParam		FromList to use/append to.
	 *
	 * @return	ResultSetNode		Returns this.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode bindNonVTITables(DataDictionary dataDictionary,
						   FromList fromListParam) 
		throws StandardException {

		// verify that the cursor exists

		preStmt = getCursorStatement();

		if (preStmt == null) {
			throw StandardException.newException(SQLState.LANG_CURSOR_NOT_FOUND, 
						cursorName);
		}
		
        preStmt.rePrepare(getLanguageConnectionContext());

		// verify that the cursor is updatable (UPDATE is responsible
		// for checking that the right columns are updatable)
		if (preStmt.getUpdateMode() != CursorNode.UPDATE)
		{
			String printableString = (cursorName == null) ? "" : cursorName;
			throw StandardException.newException(SQLState.LANG_CURSOR_NOT_UPDATABLE, printableString);
		}

		ExecCursorTableReference refTab = preStmt.getTargetTable();
		String schemaName = refTab.getSchemaName();
		exposedTableName = makeTableName(null, refTab.getExposedName());
		baseTableName = makeTableName(schemaName,
									  refTab.getBaseName());
        SchemaDescriptor tableSchema =
                getSchemaDescriptor(refTab.getSchemaName());

		/*
		** This will only happen when we are binding against a publication
		** dictionary w/o the schema we are interested in.
		*/
		if (tableSchema == null)
		{
			throw StandardException.newException(SQLState.LANG_SCHEMA_DOES_NOT_EXIST, refTab.getSchemaName());
		}

		/* Create dependency on target table, in case table not named in 
		 * positioned update/delete.  Make sure we find the table descriptor,
		 * we may fail to find it if we are binding a publication.
		 */
		TableDescriptor td = getTableDescriptor(refTab.getBaseName(), tableSchema);

		if (td == null)
		{
			throw StandardException.newException(SQLState.LANG_TABLE_NOT_FOUND, refTab.getBaseName());
		}


		/*
		** Add all the result columns from the target table.
		** For now, all updatable cursors have all columns
		** from the target table.  In the future, we should
		** relax this so that the cursor may do a partial
		** read and then the current of should make sure that
		** it can go to the base table to get all of the 
		** columns needed by the referencing positioned
		** DML.  In the future, we'll probably need to get
		** the result columns from preparedStatement and
		** turn them into an RCL that we can run with.
		*/
        setResultColumns( new ResultColumnList(getContextManager()) );
		ColumnDescriptorList cdl = td.getColumnDescriptorList();
		int					 cdlSize = cdl.size();

		for (int index = 0; index < cdlSize; index++)
		{
			/* Build a ResultColumn/BaseColumnNode pair for the column */
            ColumnDescriptor colDesc = cdl.elementAt(index);

            BaseColumnNode bcn = new BaseColumnNode(
                                            colDesc.getColumnName(),
									  		exposedTableName,
											colDesc.getType(),
											getContextManager());
            ResultColumn rc = new ResultColumn(
                colDesc, bcn, getContextManager());

			/* Build the ResultColumnList to return */
			getResultColumns().addResultColumn(rc);
		}

		/* Assign the tableNumber */
		if (tableNumber == -1)  // allow re-bind, in which case use old number
			tableNumber = getCompilerContext().getNextTableNumber();

		return this;
	}

 
	/**
	 * Try to find a ResultColumn in the table represented by this CurrentOfNode
	 * that matches the name in the given ColumnReference.
	 *
	 * @param columnReference	The columnReference whose name we're looking
	 *				for in the given table.
	 *
	 * @return	A ResultColumn whose expression is the ColumnNode
	 *			that matches the ColumnReference.
	 *		Returns null if there is no match.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultColumn getMatchingColumn(ColumnReference columnReference)
						throws StandardException {

        // if this is a dummy CurrentOfNode cooked up to compile a DELETE action
        // of a MERGE statement, then short-circuit the matching column lookup
        if ( dummyTargetTable != null ) { return dummyTargetTable.getMatchingColumn( columnReference ); }

		ResultColumn	resultColumn = null;
		TableName		columnsTableName;

		columnsTableName = columnReference.getQualifiedTableName();

        if (columnsTableName != null
                && columnsTableName.getSchemaName() == null
                && correlationName == null) {
            columnsTableName.bind();
        }

		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(preStmt!=null, "must have prepared statement");
		}

		/*
		 * We use the base table name of the target table.
		 * This is necessary since we will be comparing with the table in
		 * the delete or update statement which doesn't have a correlation
		 * name.  The select for which this column is created might have a
		 * correlation name and so we won't find it if we look for exposed names
		 * We shouldn't have to worry about multiple table since there should be
		 * only one table. Beetle 4419
		 */
		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(baseTableName!=null,"no name on target table");
		}

        if (baseTableName != null
                && baseTableName.getSchemaName() == null
                && correlationName == null) {
            baseTableName.bind();
        }

		/*
		 * If the column did not specify a name, or the specified name
		 * matches the table we're looking at, see whether the column
		 * is in this table, and also whether it is in the for update list.
		*/
		if (
			   (columnsTableName == null) ||
			   (columnsTableName.getFullTableName().equals(baseTableName.getFullTableName())) ||
			   ((correlationName != null) && correlationName.equals( columnsTableName.getTableName()))
		   )
		{
            boolean notfound;

			resultColumn =
				getResultColumns().getResultColumn(columnReference.getColumnName());

			if (resultColumn != null) 
			{
				// If we found the ResultColumn, set the ColumnReference's
				// table number accordingly.  Note: we used to only set
				// the tableNumber for correlated references (as part of
				// changes for DERBY-171) but inspection of code (esp.
				// the comments in FromList.bindColumnReferences() and
				// the getMatchingColumn() methods on other FromTables)
				// suggests that we should always set the table number
				// if we've found the ResultColumn.  So we do that here.
				columnReference.setTableNumber( tableNumber );
                columnReference.setColumnNumber(
                   resultColumn.getColumnPosition());

				// If there is a result column, are we really updating it?
				// If so, verify that the column is updatable as well
				notfound = 
					(resultColumn.updatableByCursor() &&
                     !preStmt.isUpdateColumn(columnReference.getColumnName()));
			}
			else 
			{
				notfound = true;
			}

			if (notfound)
			{
				String printableString = (cursorName == null) ? "" : cursorName;
				throw StandardException.newException(SQLState.LANG_COLUMN_NOT_UPDATABLE_IN_CURSOR, 
						 columnReference.getColumnName(), printableString);
			}
		}

		return resultColumn;
	}

	/**
	 * Preprocess a CurrentOfNode.  For a CurrentOfNode, this simply means allocating
	 * a referenced table map to avoid downstream NullPointerExceptions.
	 * NOTE: There are no bits set in the referenced table map.
	 *
	 * @param numTables			The number of tables in the DML Statement
	 * @param gbl				The group by list, if any
	 * @param fromList			The from list, if any
	 *
	 * @return ResultSetNode at top of preprocessed tree.
	 *
	 * @exception StandardException		Thrown on error
	 */
    @Override
    ResultSetNode preprocess(int numTables,
									GroupByList gbl,
									FromList fromList)
								throws StandardException
	{
		/* Generate an empty referenced table map */
		setReferencedTableMap( new JBitSet(numTables) );
		return this;
	}

	 

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */
    @Override
    void printSubNodes(int depth) {
		if (SanityManager.DEBUG) {
			super.printSubNodes(depth);

			printLabel(depth, "cursor: ");
		}
	}

	/**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */
    @Override
	public String toString() {
		if (SanityManager.DEBUG) {
			return "preparedStatement: " +
		    	(preStmt == null? "no prepared statement yet\n" :
			 	preStmt.toString() + "\n")+
				cursorName + "\n" +
				super.toString();
		} else {
			return "";
		}
	}

    @Override
    String  getExposedName()
	{
        // short-circuit for dummy CurrentOfNode cooked up to support
        // the DELETE action of a MERGE statement
        if ( dummyTargetTable != null ) { return dummyTargetTable.getExposedName(); }
        
		return exposedTableName.getFullTableName();
	}

    /**
     * Get the lock mode for this table as the target of an update statement
     * (a delete or update).  This is implemented only for base tables and
     * CurrentOfNodes.
     *
     * @see TransactionController
     *
     * @return  The lock mode
     */
    @Override
    public int updateTargetLockMode()
    {
        /* Do row locking for positioned update/delete */
        return TransactionController.MODE_RECORD;
    }

    //
    // class interface
    //
    TableName  getExposedTableName()
	{
		return exposedTableName;
	}

    TableName  getBaseCursorTargetTableName()
	{
		return baseTableName;
	}

    String getCursorName()
	{
		return cursorName;
	}

	/**
	 * Return the CursorNode associated with a positioned update/delete.
	 * 
	 * @return CursorNode	The associated CursorNode.
	 *
	 */
	ExecPreparedStatement getCursorStatement()
	{
		Activation activation = getLanguageConnectionContext().lookupCursorActivation(cursorName);

		if (activation == null)
			return null;

		return activation.getPreparedStatement();
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (exposedTableName != null) {
            exposedTableName = (TableName) exposedTableName.accept(v);
        }

        if (baseTableName != null) {
            baseTableName = (TableName) baseTableName.accept(v);
        }
    }
}
