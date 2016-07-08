/*

   Derby - Class org.apache.derby.impl.sql.compile.RenameNode

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

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.StatementType;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptorList;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.TupleDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A RenameNode is the root of a QueryTree that represents a
 * RENAME TABLE/COLUMN/INDEX statement.
 *
 */

class RenameNode extends DDLStatementNode
{
	protected TableName newTableName;

	// original name of the object being renamed
	protected String oldObjectName;
	// original name for that object
	protected String newObjectName;

	protected TableDescriptor	td;
	private long conglomerateNumber;

	/* You can rename using either alter table or rename command to
	 * rename a table/column. An index can only be renamed with rename
	 * command. usedAlterTable flag is used to keep that information.
	 */
	protected boolean	usedAlterTable;

	/* renamingWhat will be set to 1 if user is renaming a table.
	 * Will be set to 2 if user is renaming a column and will be
	 * set to 3 if user is renaming an index
	 */
	protected int renamingWhat;

	/**
     * Constructor for a RenameNode
	 *
	 * @param tableName The name of the table. This is the table which is
	 *		being renamed in case of rename table. In case of rename
	 *		column, the column being renamed belongs to this table.
	 *		In case of rename index, this is null because index name
	 *		is unique within a schema and doesn't have to be
     *      associated with a table name.
     *      Coming from ALTER TABLE path, tableName will
     *      be TableName object. Mostly a TableName object, but coming from
     *      RENAME COLUMN path, tableName will be a String.
	 * @param oldObjectName This is either the name of column/index in case
	 *		of rename column/index. For rename table, this is null.
	 * @param newObjectName This is new name for table/column/index
	 * @param usedAlterTable True-Used Alter Table, False-Used Rename.
	 *		For rename index, this will always be false because
	 *		there is no alter table command to rename index
	 * @param renamingWhat Rename a 1 - table, 2 - column, 3 - index
     * @param cm context manager
	 *
	 * @exception StandardException Thrown on error
	 */
    RenameNode(Object tableName,
               String oldObjectName,
               String newObjectName,
               boolean usedAlterTable,
               int renamingWhat,
               ContextManager cm) throws StandardException
	{
        super(cm);
        this.usedAlterTable = usedAlterTable;
        this.renamingWhat = renamingWhat;

		switch (this.renamingWhat)
		{
			case StatementType.RENAME_TABLE:
				initAndCheck((TableName) tableName);
				this.newTableName =
                    makeTableName(getObjectName().getSchemaName(), newObjectName);
				this.oldObjectName = null;
				this.newObjectName = this.newTableName.getTableName();
				break;

			case StatementType.RENAME_COLUMN:
				/* coming from ALTER TABLE path, tableName will
				 * be TableName object. Coming from RENAME COLUMN
				 * path, tableName will be just a String.
				 */
				TableName actingObjectName;
				if (tableName instanceof TableName)
					actingObjectName = (TableName) tableName;
				else
					actingObjectName = makeTableName(null,
					(String)tableName);
				initAndCheck(actingObjectName);


                this.oldObjectName = oldObjectName;
                this.newObjectName = newObjectName;
				break;

			case StatementType.RENAME_INDEX:
                this.oldObjectName = oldObjectName;
                this.newObjectName = newObjectName;
				break;

			default:
				if (SanityManager.DEBUG) 
				SanityManager.THROWASSERT(
				"Unexpected rename action in RenameNode");
		}
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
		if (SanityManager.DEBUG) {

		switch (renamingWhat)
		{
			case StatementType.RENAME_TABLE:
				return super.toString() +
				"oldTableName: " + "\n" + getRelativeName() + "\n" +
				"newTableName: " + "\n" + newTableName + "\n" ;

			case StatementType.RENAME_COLUMN:
				return super.toString() +
				"oldTableName.oldColumnName:" + "\n" +
				getRelativeName() + "." + oldObjectName + "\n" +
				"newColumnName: " + "\n" + newObjectName + "\n" ;

			case StatementType.RENAME_INDEX:
				return super.toString() +
				"oldIndexName:" + "\n" + oldObjectName + "\n" +
				"newIndexName: " + "\n" + newObjectName + "\n" ;

			default:
				SanityManager.THROWASSERT(
				"Unexpected rename action in RenameNode");
				return "UNKNOWN";
		}
		} else {
			return "";
		}
	}

    String statementToString()
	{
		if (usedAlterTable)
			return "ALTER TABLE";
		else {
			switch (renamingWhat)
			{
				case StatementType.RENAME_TABLE:
					return "RENAME TABLE";

				case StatementType.RENAME_COLUMN:
					return "RENAME COLUMN";

				case StatementType.RENAME_INDEX:
					return "RENAME INDEX";

				default:
					if (SanityManager.DEBUG)
						SanityManager.THROWASSERT(
						"Unexpected rename action in RenameNode");
					return "UNKNOWN";
			}
    }
	}
 

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
		//If rename is on a SESSION schema table, then return true. 
		if (isSessionSchema(td.getSchemaName()))//existing table with rename action
			return true;

		//new name in rename action
		if (renamingWhat == StatementType.RENAME_TABLE && isSessionSchema(getSchemaDescriptor()))
			return true;

		return false;
	}

	//do any checking needs to be done at bind time for rename table
	private void renameTableBind(DataDictionary dd)
		throws StandardException
	{
		/* Verify that there are no check constraints on the table */
		ConstraintDescriptorList constraintDescriptorList = dd.getConstraintDescriptors(td);
		int size =
			constraintDescriptorList == null ? 0 : constraintDescriptorList.size();

		ConstraintDescriptor constraintDescriptor;

		// go through all the constraints defined on the table
		for (int index = 0; index < size; index++)
		{
			constraintDescriptor = constraintDescriptorList.elementAt(index);
			// if it is a check constraint, error
			if (constraintDescriptor.getConstraintType() == DataDictionary.CHECK_CONSTRAINT)
			{
				throw StandardException.newException(
						SQLState.LANG_PROVIDER_HAS_DEPENDENT_OBJECT,
						"RENAME",
						td.getName(),
						"CONSTRAINT",
						constraintDescriptor.getConstraintName());
			}
		}
	}
                           
	//do any checking needs to be done at bind time for rename column
	private void renameColumnBind(DataDictionary dd)
		throws StandardException
	{
		ColumnDescriptor columnDescriptor = td.getColumnDescriptor(oldObjectName);

		/* Verify that old column name does exist in the table */
		if (columnDescriptor == null)
			throw StandardException.newException(SQLState.LANG_COLUMN_NOT_FOUND_IN_TABLE,
			oldObjectName, getFullName());

		/* Verify that new column name does not exist in the table */
		ColumnDescriptor cd = td.getColumnDescriptor(newObjectName);
  		if (cd != null)
  			throw descriptorExistsException(cd, td);

        //
		// You cannot rename a column which is referenced by the generation
        // clause of a generated column.
        //
        ColumnDescriptorList    generatedColumns = td.getGeneratedColumns();
        int                                 generatedColumnCount = generatedColumns.size();
        for ( int i = 0; i < generatedColumnCount; i++ )
        {
            ColumnDescriptor    gc = generatedColumns.elementAt( i );
            String[]                    referencedColumns = gc.getDefaultInfo().getReferencedColumnNames();
            int                         refColCount = referencedColumns.length;
            for ( int j = 0; j < refColCount; j++ )
            {
                String      refName = referencedColumns[ j ];
                if ( oldObjectName.equals( refName ) )
                {
                    throw StandardException.newException( SQLState.LANG_GEN_COL_BAD_RENAME, oldObjectName, gc.getColumnName() );
                }
            }
        }

		/* Verify that there are no check constraints using the column being renamed */
		ConstraintDescriptorList constraintDescriptorList =
			dd.getConstraintDescriptors(td);
		int size =
			constraintDescriptorList == null ? 0 : constraintDescriptorList.size();

		ConstraintDescriptor constraintDescriptor;
		ColumnDescriptorList checkConstraintCDL;
		int	checkConstraintCDLSize;

		// go through all the constraints defined on the table
		for (int index = 0; index < size; index++)
		{
			constraintDescriptor = constraintDescriptorList.elementAt(index);
			// if it is a check constraint, verify that column being
			// renamed is not used in it's sql
			if (constraintDescriptor.getConstraintType() == DataDictionary.CHECK_CONSTRAINT)
			{
				checkConstraintCDL = constraintDescriptor.getColumnDescriptors();
				checkConstraintCDLSize = checkConstraintCDL.size();

				for (int index2 = 0; index2 < checkConstraintCDLSize; index2++)
					if (checkConstraintCDL.elementAt( index2 ) == columnDescriptor)
						throw StandardException.newException(
						SQLState.LANG_RENAME_COLUMN_WILL_BREAK_CHECK_CONSTRAINT,
						oldObjectName,
						constraintDescriptor.getConstraintName());
			}
		}
	}
                           
	/**
	 * Create the Constant information that will drive the guts of Execution
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction   makeConstantAction()
		throws StandardException
	{
		return	getGenericConstantActionFactory().getRenameConstantAction(getFullName(),
			getRelativeName(),
			oldObjectName,
			newObjectName,
			getSchemaDescriptor(),
			td.getUUID(),
			usedAlterTable,
			renamingWhat);
	}

	private StandardException descriptorExistsException(TupleDescriptor tuple,
														TupleDescriptor parent)
	{
		return
			StandardException.newException(SQLState.LANG_OBJECT_ALREADY_EXISTS_IN_OBJECT,
										   tuple.getDescriptorType(),
										   tuple.getDescriptorName(),
										   parent.getDescriptorType(),
										   parent.getDescriptorName());
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (newTableName != null) {
            newTableName = (TableName) newTableName.accept(v);
        }
    }
}
