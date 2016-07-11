/*

   Derby - Class org.apache.derby.impl.sql.compile.TableElementList

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.derby.catalog.UUID;
import org.apache.derby.catalog.types.DefaultInfoImpl;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.Property;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.FormatableBitSet; 
import org.apache.derby.shared.common.sanity.SanityManager;
import org.apache.derby.iapi.sql.StatementType;
import org.apache.derby.iapi.sql.compile.CompilerContext;  
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptor;
import org.apache.derby.iapi.sql.dictionary.ConstraintDescriptorList;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.TypeId; 

/**
 * A TableElementList represents the list of columns and other table elements
 * such as constraints in a CREATE TABLE or ALTER TABLE statement.
 *
 */

class TableElementList extends QueryTreeNodeVector<TableElementNode>
{
	private int				numColumns;
	private TableDescriptor td;

    public TableElementList(ContextManager cm) {
        super(TableElementNode.class, cm);
    }

	/**
	 * Add a TableElementNode to this TableElementList
	 *
	 * @param tableElement	The TableElementNode to add to this list
	 */

    void addTableElement(TableElementNode tableElement)
	{
		addElement(tableElement);
		if ((tableElement instanceof ColumnDefinitionNode) ||
			tableElement.getElementType() == TableElementNode.AT_DROP_COLUMN)
		{
			numColumns++;
		}
	} 

	/**
	 * Use the passed schema descriptor's collation type to set the collation
	 * of the character string types in create table node
	 * @param sd
	 */
	void setCollationTypesOnCharacterStringColumns(SchemaDescriptor sd)
        throws StandardException
    {
        for (TableElementNode te : this)
		{
            if (te instanceof ColumnDefinitionNode)
			{
                setCollationTypeOnCharacterStringColumn(
                        sd, (ColumnDefinitionNode)te );
			}
		}
	}

	/**
	 * Use the passed schema descriptor's collation type to set the collation
	 * of a character string column.
	 * @param sd
	 */
	void setCollationTypeOnCharacterStringColumn(SchemaDescriptor sd, ColumnDefinitionNode cdn )
        throws StandardException
    {
		int collationType = sd.getCollationType();

        //
        // Only generated columns can omit the datatype specification during the
        // early phases of binding--before we have been able to bind the
        // generation clause.
        //
        DataTypeDescriptor  dtd = cdn.getType();
        if ( dtd == null )
        {
            if ( !cdn.hasGenerationClause() )
            {
                throw StandardException.newException
                    ( SQLState.LANG_NEEDS_DATATYPE, cdn.getColumnName() );
            }
        }
        else
        {
            if ( dtd.getTypeId().isStringTypeId() ) { cdn.setCollationType(collationType); }
        }
    }
    
	/**
	 * Validate this TableElementList.  This includes checking for
	 * duplicate columns names, and checking that user types really exist.
	 *
	 * @param ddlStmt	DDLStatementNode which contains this list
	 * @param dd		DataDictionary to use
	 * @param td		TableDescriptor for table, if existing table.
	 *
	 * @exception StandardException		Thrown on error
	 */
	void validate(DDLStatementNode ddlStmt,
					     DataDictionary dd,
						 TableDescriptor td)
					throws StandardException
	{
 		this.td = td;
		int numAutoCols = 0;

		int			size = size();
        HashSet<String> columnNames = new HashSet<String>(size + 2, 0.999f);
        HashSet<String> constraintNames = new HashSet<String>(size + 2, 0.999f);
		//all the primary key/unique key constraints for this table
        ArrayList<Object> constraints = new ArrayList<Object>();

		//special case for alter table (td is not null in case of alter table)
		if (td != null)
		{
			//In case of alter table, get the already existing primary key and unique
			//key constraints for this table. And then we will compare them with  new
			//primary key/unique key constraint column lists.
			ConstraintDescriptorList cdl = dd.getConstraintDescriptors(td);
			ConstraintDescriptor cd;

			if (cdl != null) //table does have some pre-existing constraints defined on it
			{
				for (int i=0; i<cdl.size();i++)
				{
					cd = cdl.elementAt(i);
					//if the constraint type is not primary key or unique key, ignore it.
					if (cd.getConstraintType() == DataDictionary.PRIMARYKEY_CONSTRAINT ||
					cd.getConstraintType() == DataDictionary.UNIQUE_CONSTRAINT)
                    {
                        constraints.add(cd);
                    }
				}
			}
		}

		int tableType = TableDescriptor.BASE_TABLE_TYPE;
		if (ddlStmt instanceof CreateTableNode)
			tableType = ((CreateTableNode)ddlStmt).tableType;

        for (TableElementNode tableElement : this)
		{
			if (tableElement instanceof ColumnDefinitionNode)
			{
                ColumnDefinitionNode cdn = (ColumnDefinitionNode)tableElement;
				if (tableType == TableDescriptor.GLOBAL_TEMPORARY_TABLE_TYPE &&
					(cdn.getType().getTypeId().isLongConcatableTypeId() ||
					cdn.getType().getTypeId().isUserDefinedTypeId()))
				{
					throw StandardException.newException(SQLState.LANG_LONG_DATA_TYPE_NOT_ALLOWED, cdn.getColumnName());
				}
				checkForDuplicateColumns(ddlStmt, columnNames, cdn.getColumnName());
				cdn.checkUserType(td);
				cdn.bindAndValidateDefault(dd, td);

				cdn.validateAutoincrement(dd, td, tableType);

				if (tableElement instanceof ModifyColumnNode)
				{
					ModifyColumnNode mcdn = (ModifyColumnNode)cdn;
					mcdn.checkExistingConstraints(td);
					mcdn.useExistingCollation(td);

				} else if (cdn.isAutoincrementColumn())
                { numAutoCols ++; }
			}
			else if (tableElement.getElementType() == TableElementNode.AT_DROP_COLUMN)
			{
				String colName = tableElement.getName();
				if (td.getColumnDescriptor(colName) == null)
				{
					throw StandardException.newException(
												SQLState.LANG_COLUMN_NOT_FOUND_IN_TABLE,
												colName,
												td.getQualifiedName());
				}
				break;
			}

			/* The rest of this method deals with validating constraints */
			if (! (tableElement.hasConstraint()))
			{
				continue;
			}

			ConstraintDefinitionNode cdn = (ConstraintDefinitionNode) tableElement;

			cdn.bind(ddlStmt, dd);

            // If constraint is primary key or unique key, add it to the list.
			if (cdn.getConstraintType() == DataDictionary.PRIMARYKEY_CONSTRAINT ||
			cdn.getConstraintType() == DataDictionary.UNIQUE_CONSTRAINT)
			{
                /* In case of create table, the list can have only ConstraintDefinitionNode
				* elements. In case of alter table, it can have both ConstraintDefinitionNode
				* (for new constraints) and ConstraintDescriptor(for pre-existing constraints).
				*/

				Object destConstraint;
				String destName = null;
				String[] destColumnNames = null;

                for (int i = 0; i < constraints.size(); i++)
				{

                    destConstraint = constraints.get(i);
					if (destConstraint instanceof ConstraintDefinitionNode)
					{
						ConstraintDefinitionNode destCDN = (ConstraintDefinitionNode)destConstraint;
						destName = destCDN.getConstraintMoniker();
						destColumnNames = destCDN.getColumnList().getColumnNames();
					}
					else if (destConstraint instanceof ConstraintDescriptor)
					{
						//will come here only for pre-existing constraints in case of alter table
						ConstraintDescriptor destCD = (ConstraintDescriptor)destConstraint;
						destName = destCD.getConstraintName();
						destColumnNames = destCD.getColumnDescriptors().getColumnNames();
					}
					//check if there are multiple constraints with same set of columns
					if (columnsMatch(cdn.getColumnList().getColumnNames(), destColumnNames))
						throw StandardException.newException(SQLState.LANG_MULTIPLE_CONSTRAINTS_WITH_SAME_COLUMNS,
						cdn.getConstraintMoniker(), destName);
				}
                constraints.add(cdn);
			}

			/* Make sure that there are no duplicate constraint names in the list */
            checkForDuplicateConstraintNames(ddlStmt, constraintNames, cdn.getConstraintMoniker());

			/* Make sure that the constraint we are trying to drop exists */
            if (cdn.getConstraintType() == DataDictionary.DROP_CONSTRAINT ||
                cdn.getConstraintType() == DataDictionary.MODIFY_CONSTRAINT)
			{
				/*
				** If no schema descriptor, then must be an invalid
				** schema name.
				*/

				String dropConstraintName = cdn.getConstraintMoniker();

				if (dropConstraintName != null) {

					String dropSchemaName = cdn.getDropSchemaName();

					SchemaDescriptor sd = dropSchemaName == null ? td.getSchemaDescriptor() :
											getSchemaDescriptor(dropSchemaName);

					ConstraintDescriptor cd =
								dd.getConstraintDescriptorByName(
										td, sd, dropConstraintName,
										false);
					if (cd == null)
					{
                        throw StandardException.newException(SQLState.LANG_DROP_OR_ALTER_NON_EXISTING_CONSTRAINT,
								(sd.getSchemaName() + "."+ dropConstraintName),
								td.getQualifiedName());
					}
				 
				}
			}

            // validation of primary key nullability moved to validatePrimaryKeyNullability().
            if (cdn.hasPrimaryKeyConstraint())
            {
                // for PRIMARY KEY, check that columns are unique
                verifyUniqueColumnList(ddlStmt, cdn);
            }
            else if (cdn.hasUniqueKeyConstraint())
            {
                // for UNIQUE, check that columns are unique
                verifyUniqueColumnList(ddlStmt, cdn);

                // unique constraints on nullable columns added in 10.4, 
                // disallow until database hard upgraded at least to 10.4.
                if (!dd.checkVersion(
                        DataDictionary.DD_VERSION_DERBY_10_4, null))
                {
                    checkForNullColumns(cdn, td);
                }
            }
            else if (cdn.hasForeignKeyConstraint())
            {
                // for FOREIGN KEY, check that columns are unique
                verifyUniqueColumnList(ddlStmt, cdn);
            }
		}

		/* Can have only one autoincrement column in DB2 mode */
		if (numAutoCols > 1)
			throw StandardException.newException(SQLState.LANG_MULTIPLE_AUTOINCREMENT_COLUMNS);

	}

    /**
	 * Validate nullability of primary keys. This logic was moved out of the main validate
	 * method so that it can be called after binding generation clauses. We need
	 * to perform the nullability checks later on because the datatype may be
	 * omitted on the generation clause--we can't set/vet the nullability of the
	 * datatype until we determine what the datatype is.
	 */
    public  void    validatePrimaryKeyNullability()
        throws StandardException
    {
        for (TableElementNode tableElement : this)
		{
			if (! (tableElement.hasConstraint()))
			{
				continue;
			}
            
			ConstraintDefinitionNode cdn = (ConstraintDefinitionNode) tableElement;

            if (cdn.hasPrimaryKeyConstraint())
            {
                if (td == null)
                {
                    // in CREATE TABLE so set PRIMARY KEY columns to NOT NULL
                    setColumnListToNotNull(cdn);
                }
                else
                {
                    // in ALTER TABLE so raise error if any columns are nullable
                    checkForNullColumns(cdn, td);
                }
            }
        }
    }
    
    /**
	 * Count the number of constraints of the specified type.
	 *
	 * @param constraintType	The constraint type to search for.
	 *
	 * @return int	The number of constraints of the specified type.
	 */
    int countConstraints(int constraintType)
	{
		int	numConstraints = 0;

        for (TableElementNode element : this)
		{
            if (element instanceof ConstraintDefinitionNode &&
                ((ConstraintDefinitionNode)element).getConstraintType() ==
                    constraintType) {
                numConstraints++;
            }
		}

		return numConstraints;
	}

    /**
	 * Count the number of generation clauses.
	 */
    int countGenerationClauses()
	{
		int	numGenerationClauses = 0;

        for (TableElementNode element : this)
		{
            if (element instanceof ColumnDefinitionNode &&
                    ((ColumnDefinitionNode)element).hasGenerationClause()) {
                numGenerationClauses++;
            }
		}

		return numGenerationClauses;
	}

	/**
	 * Count the number of columns.
	 *
	 * @return int	The number of columns.
	 */
    int countNumberOfColumns()
	{
		return numColumns;
	}

	 
	/**
	 * Append goobered up ResultColumns to the table's RCL.
	 * This is useful for binding check constraints for CREATE and ALTER TABLE.
	 *
	 * @param table		The table in question.
	 *
	 * @exception StandardException		Thrown on error
	 */
    void appendNewColumnsToRCL(FromBaseTable table)
		throws StandardException
	{
		int				 size = size();
		ResultColumnList rcl = table.getResultColumns();
		TableName		 exposedName = table.getTableName();

		for (int index = 0; index < size; index++)
		{
			if (elementAt(index) instanceof ColumnDefinitionNode)
			{
				ColumnDefinitionNode cdn = (ColumnDefinitionNode) elementAt(index);
				ResultColumn	resultColumn;
				ValueNode		valueNode;

				/* Build a ResultColumn/BaseColumnNode pair for the column */
                valueNode = new BaseColumnNode(cdn.getColumnName(),
									  		exposedName,
											cdn.getType(),
											getContextManager());

                resultColumn = new ResultColumn(
                        cdn.getType(), valueNode, getContextManager());
				resultColumn.setName(cdn.getColumnName());
				rcl.addElement(resultColumn);
			}
		}
	}

	/**
	 * Bind and validate all of the check constraints in this list against
	 * the specified FromList.  
	 *
	 * @param fromList		The FromList in question.
	 *
	 * @exception StandardException		Thrown on error
	 */
	void bindAndValidateCheckConstraints(FromList fromList)
		throws StandardException
	{
        FromBaseTable table = (FromBaseTable) fromList.elementAt(0);
        CompilerContext cc = getCompilerContext();

        ArrayList<AggregateNode> aggregates = new ArrayList<AggregateNode>();

        for (TableElementNode element : this)
		{
			ConstraintDefinitionNode cdn;
			ValueNode	checkTree;

			if (! (element instanceof ConstraintDefinitionNode))
			{
				continue;
			}

			cdn = (ConstraintDefinitionNode) element;

			if (cdn.getConstraintType() != DataDictionary.CHECK_CONSTRAINT)
			{
				continue;
			}

			checkTree = cdn.getCheckCondition();

			// bind the check condition
			// verify that it evaluates to a boolean
			final int previousReliability = cc.getReliability();
			try
			{ }
			finally
			{
				cc.setReliability(previousReliability);
			}
	
            /* We have a valid check constraint.
             * Now we build a list with only the referenced columns and
			 * copy it to the cdn.  Thus we can build the array of
			 * column names for the referenced columns during generate().
			 */
            ResultColumnList rcl = table.getResultColumns();
            int numReferenced = rcl.countReferencedColumns();
            ResultColumnList refRCL = new ResultColumnList(getContextManager());
			rcl.copyReferencedColumnsToNewList(refRCL);

			/* A column check constraint can only refer to that column. If this is a
			 * column constraint, we should have an RCL with that column
			 */
			if (cdn.getColumnList() != null)
			{
                String colName = cdn.getColumnList().elementAt(0).getName();
				if (numReferenced > 1 ||
                    !colName.equals(refRCL.elementAt(0).getName()))
					throw StandardException.newException(SQLState.LANG_DB2_INVALID_CHECK_CONSTRAINT, colName);
				
			}
			cdn.setColumnList(refRCL);

			/* Clear the column references in the RCL so each check constraint
			 * starts with a clean list.
			 */
			rcl.clearColumnReferences();

            // Make sure all names are schema qualified (DERBY-6362)
            cdn.qualifyNames();
		}
	}

	/**
	 * Bind and validate all of the generation clauses in this list against
	 * the specified FromList.  
	 *
	 * @param sd			Schema where the table lives.
	 * @param fromList		The FromList in question.
	 * @param generatedColumns Bitmap of generated columns in the table. Vacuous for CREATE TABLE, but may be non-trivial for ALTER TABLE. This routine may set bits for new generated columns.
	 * @param baseTable  Table descriptor if this is an ALTER TABLE statement.
	 *
	 * @exception StandardException		Thrown on error
	 */
	void bindAndValidateGenerationClauses( SchemaDescriptor sd, FromList fromList, FormatableBitSet generatedColumns, TableDescriptor baseTable )
		throws StandardException
	{
        FromBaseTable    table = (FromBaseTable) fromList.elementAt(0);
        ResultColumnList tableColumns = table.getResultColumns();
        int              columnCount = table.getResultColumns().size();

        // complain if a generation clause references another generated column
        findIllegalGenerationReferences( fromList, baseTable );

        generatedColumns.grow( columnCount + 1 );
        
        CompilerContext cc = getCompilerContext();

        ArrayList<AggregateNode> aggregates = new ArrayList<AggregateNode>();

        for (TableElementNode element : this)
		{
			ColumnDefinitionNode cdn;
            GenerationClauseNode    generationClauseNode;
			ValueNode	generationTree;

			if (! (element instanceof ColumnDefinitionNode))
			{
				continue;
			}

			cdn = (ColumnDefinitionNode) element;

			if (!cdn.hasGenerationClause())
			{
				continue;
			}

            generationClauseNode = cdn.getGenerationClauseNode();

			// bind the generation clause
			final int previousReliability = cc.getReliability();
           

			/* We have a valid generation clause, now build an array of
			 * 1-based columnIds that the clause references.
			 */
			ResultColumnList rcl = table.getResultColumns();
			int		numReferenced = rcl.countReferencedColumns();
			int[]	generationClauseColumnReferences = new int[numReferenced];
            int     position = rcl.getPosition( cdn.getColumnName(), 1 );

            generatedColumns.set( position );
        
			rcl.recordColumnReferences(generationClauseColumnReferences, 1);

            String[]    referencedColumnNames = new String[ numReferenced ];

            for ( int i = 0; i < numReferenced; i++ )
            {
                referencedColumnNames[i] =
                    rcl.elementAt(generationClauseColumnReferences[i] - 1).
                        getName();
            }

            String              currentSchemaName = getLanguageConnectionContext().getCurrentSchemaName();
            DefaultInfoImpl dii = new DefaultInfoImpl
                ( generationClauseNode.getExpressionText(), referencedColumnNames, currentSchemaName );
            cdn.setDefaultInfo( dii );

			/* Clear the column references in the RCL so each generation clause
			 * starts with a clean list.
			 */
			rcl.clearColumnReferences();
		}

        
	}

	/**
	 * Complain if a generation clause references other generated columns. This
	 * is required by the SQL Standard, part 2, section 4.14.8.
	 *
	 * @param fromList		The FromList in question.
	 * @param baseTable  Table descriptor if this is an ALTER TABLE statement.
	 * @exception StandardException		Thrown on error
	 */
	void findIllegalGenerationReferences( FromList fromList, TableDescriptor baseTable )
		throws StandardException
	{
        ArrayList<ColumnDefinitionNode>   generatedColumns = new ArrayList<ColumnDefinitionNode>();
        HashSet<String>     names = new HashSet<String>();

        // add in existing generated columns if this is an ALTER TABLE statement
        if ( baseTable != null )
        {
            ColumnDescriptorList cdl = baseTable.getGeneratedColumns();
            int                  count = cdl.size();
            for ( int i = 0; i < count; i++ )
            {
                names.add( cdl.elementAt( i ).getColumnName() );
            }
        }
        
        // find all of the generated columns
        for (TableElementNode element : this)
		{
			ColumnDefinitionNode cdn;

			if (! (element instanceof ColumnDefinitionNode)) { continue; }

			cdn = (ColumnDefinitionNode) element;

			if (!cdn.hasGenerationClause()) { continue; }

            generatedColumns.add( cdn );
            names.add( cdn.getColumnName() );
        }

        // now look at their generation clauses to see if they reference one
        // another
        int    count = generatedColumns.size();
        for ( int i = 0; i < count; i++ )
        {
            ColumnDefinitionNode    cdn = generatedColumns.get( i );
            GenerationClauseNode    generationClauseNode = cdn.getGenerationClauseNode();
            List<ColumnReference>   referencedColumns =
                generationClauseNode.findReferencedColumns();
            int                     refCount = referencedColumns.size();
            for ( int j = 0; j < refCount; j++ )
            {
                String name = referencedColumns.get(j).getColumnName();

                if ( name != null )
                {
                    if ( names.contains( name ) )
                    {
                        throw StandardException.newException(SQLState.LANG_CANT_REFERENCE_GENERATED_COLUMN, cdn.getColumnName());
                    }
                }
            }
        }

    }
    
	/**
	 * Prevent foreign keys on generated columns from violating the SQL spec,
	 * part 2, section 11.8 (<column definition>), syntax rule 12: the
	 * referential action may not specify SET NULL or SET DEFAULT and the update
	 * rule may not specify ON UPDATE CASCADE.  
	 *
	 * @param fromList		The FromList in question.
	 * @param generatedColumns Bitmap of generated columns in the table.
	 *
	 * @exception StandardException		Thrown on error
	 */
	void validateForeignKeysOnGenerationClauses(FromList fromList, FormatableBitSet generatedColumns )
		throws StandardException
	{
        // nothing to do if there are no generated columns
        if ( generatedColumns.getNumBitsSet() <= 0 ) { return; }
        
		FromBaseTable				table = (FromBaseTable) fromList.elementAt(0);
        ResultColumnList        tableColumns = table.getResultColumns();

        
    }
    
	 

      //check if one array is same as another 
	private boolean columnsMatch(String[] columnNames1, String[] columnNames2)
	{
		int srcCount, srcSize, destCount,destSize;

		if (columnNames1.length != columnNames2.length)
			return false;

		srcSize = columnNames1.length;
		destSize = columnNames2.length;

		for (srcCount = 0; srcCount < srcSize; srcCount++)
		{
            boolean match = false;
			for (destCount = 0; destCount < destSize; destCount++) {
				if (columnNames1[srcCount].equals(columnNames2[destCount])) {
					match = true;
					break;
				}
			}
			if (match == false)
				return false;
		}

		return true;
	}
 
    /**
     * Checks if the index should use a larger page size.
     *
     * If the columns in the index are large, and if the user hasn't already
     * specified a page size to use, then we may need to default to the
     * large page size in order to get an index with sufficiently large pages.
     * For example, this DDL should use a larger page size for the index
     * that backs the PRIMARY KEY constraint:
     *
     * create table t (x varchar(1000) primary key)
     *
     * @param cdn Constraint node
     *
     * @return properties to use for creating the index
     */
    private Properties checkIndexPageSizeProperty(ConstraintDefinitionNode cdn) 
        throws StandardException
    {return null;}


	/**
	 * Check to make sure that there are no duplicate column names
	 * in the list.  (The comparison here is case sensitive.
	 * The work of converting column names that are not quoted
	 * identifiers to upper case is handled by the parser.)
	 * RESOLVE: This check will also be performed by alter table.
	 *
	 * @param ddlStmt	DDLStatementNode which contains this list
     * @param seenNames The column names seen so far (for enforcing uniqueness)
	 * @param colName	Column name to check for.
	 *
	 * @exception StandardException		Thrown on error
	 */
	private void checkForDuplicateColumns(DDLStatementNode ddlStmt,
									Set<String> seenNames,
									String colName)
			throws StandardException
	{
		if (!seenNames.add(colName))
		{
			/* RESOLVE - different error messages for create and alter table */
			if (ddlStmt instanceof CreateTableNode)
			{
				throw StandardException.newException(SQLState.LANG_DUPLICATE_COLUMN_NAME_CREATE, colName);
			}
		}
	}

	/**
	 * Check to make sure that there are no duplicate constraint names
	 * in the list.  (The comparison here is case sensitive.
	 * The work of converting column names that are not quoted
	 * identifiers to upper case is handled by the parser.)
	 * RESOLVE: This check will also be performed by alter table.
	 *
	 * @param ddlStmt	DDLStatementNode which contains this list
     * @param seenNames The constraint names seen so far (for enforcing
     *                  uniqueness)
	 *
	 * @exception StandardException		Thrown on error
	 */
	private void checkForDuplicateConstraintNames(DDLStatementNode ddlStmt,
									Set<String> seenNames,
									String constraintName)
			throws StandardException
	{
		if (constraintName == null)
			return;

		if (!seenNames.add(constraintName)) {

			/* RESOLVE - different error messages for create and alter table */
			if (ddlStmt instanceof CreateTableNode)
			{
				/* RESOLVE - new error message */
				throw StandardException.newException(SQLState.LANG_DUPLICATE_CONSTRAINT_NAME_CREATE, 
						constraintName);
			}
		}
	}

	/**
	 * Verify that a primary/unique table constraint has a valid column list.
	 * (All columns in table and no duplicates.)
	 *
	 * @param ddlStmt	The outer DDLStatementNode
	 * @param cdn		The ConstraintDefinitionNode
	 *
	 * @exception	StandardException	Thrown if the column list is invalid
	 */
	private void verifyUniqueColumnList(DDLStatementNode ddlStmt,
								ConstraintDefinitionNode cdn)
		throws StandardException
	{
		String invalidColName;

		/* Verify that every column in the list appears in the table's list of columns */
		if (ddlStmt instanceof CreateTableNode)
		{
			invalidColName = cdn.getColumnList().verifyCreateConstraintColumnList(this);
			if (invalidColName != null)
			{
				throw StandardException.newException(SQLState.LANG_INVALID_CREATE_CONSTRAINT_COLUMN_LIST, 
								ddlStmt.getRelativeName(),
								invalidColName);
			}
		}
		else
		{
			/* RESOLVE - alter table will need to get table descriptor */
		}

		/* Check the uniqueness of the column names within the list */
		invalidColName = cdn.getColumnList().verifyUniqueNames(false);
		if (invalidColName != null)
		{
			throw StandardException.newException(SQLState.LANG_DUPLICATE_CONSTRAINT_COLUMN_NAME, invalidColName);
		}
	}

	/**
	 * Set all columns in that appear in a PRIMARY KEY constraint in a CREATE TABLE statement to NOT NULL.
	 *
	 * @param cdn		The ConstraintDefinitionNode for a PRIMARY KEY constraint
	 */
	private void setColumnListToNotNull(ConstraintDefinitionNode cdn)
	{
        for (ResultColumn rc : cdn.getColumnList())
		{
            findColumnDefinition(rc.getName()).setNullability(false);
        }
	}

    /**
     * Checks if any of the columns in the constraint can be null.
     *
     * @param cdn Constraint node
     * @param td tabe descriptor of the target table
     *
     * @return true if any of the column can be null false other wise
     */
    private boolean areColumnsNullable (
    ConstraintDefinitionNode    cdn, 
    TableDescriptor             td) 
    {
        for (ResultColumn rc : cdn.getColumnList())
        {
            String colName = rc.getName();

            DataTypeDescriptor dtd = (td == null) ?
                getColumnDataTypeDescriptor(colName) :
                getColumnDataTypeDescriptor(colName, td);

            // todo dtd may be null if the column does not exist, we should check that first
            if (dtd != null && dtd.isNullable())
            {
                return true;
            }
        }
        return false;
    }

    private void checkForNullColumns(ConstraintDefinitionNode cdn, TableDescriptor td) throws StandardException
    {
        for (ResultColumn rc : cdn.getColumnList())
        {
            DataTypeDescriptor dtd = (td == null) ?
                    getColumnDataTypeDescriptor(rc.getName()) :
                    getColumnDataTypeDescriptor(rc.getName(), td);

            // todo dtd may be null if the column does not exist, we should check that first
            if (dtd != null && dtd.isNullable())
            {
                String errorState = 
                   (getLanguageConnectionContext().getDataDictionary()
                        .checkVersion(DataDictionary.DD_VERSION_DERBY_10_4, null))
                    ? SQLState.LANG_ADD_PRIMARY_KEY_ON_NULL_COLS
                    : SQLState.LANG_DB2_ADD_UNIQUE_OR_PRIMARY_KEY_ON_NULL_COLS;

                throw StandardException.newException(errorState, rc.getName());
            }
        }
    }

    private DataTypeDescriptor getColumnDataTypeDescriptor(String colName)
    {
        ColumnDefinitionNode col = findColumnDefinition(colName);
        if (col != null)
            return col.getType();

        return null;
    }

    private DataTypeDescriptor getColumnDataTypeDescriptor(String colName, TableDescriptor td)
    {
        // check existing columns
        ColumnDescriptor cd = td.getColumnDescriptor(colName);
        if (cd != null)
        {
            return cd.getType();
        }
        // check for new columns
        return getColumnDataTypeDescriptor(colName);
    }
    
    /**
     * Find the column definition node in this list that matches
     * the passed in column name.
     * @param colName
     * @return Reference to column definition node or null if the column is
     * not in the list.
     */
    private ColumnDefinitionNode findColumnDefinition(String colName) {
        for (TableElementNode te : this) {
            if (te instanceof ColumnDefinitionNode) {
                ColumnDefinitionNode cdn = (ColumnDefinitionNode) te;
                if (colName.equals(cdn.getName())) {
                    return cdn;
                }
            }
        }
        return null;
    }
    

	/**
     * Determine whether or not the parameter matches a column name in this
     * list.
     * 
     * @param colName
     *            The column name to search for.
     * 
     * @return boolean Whether or not a match is found.
     */
    boolean containsColumnName(String colName)
	{
        return findColumnDefinition(colName) != null;
	}
}

