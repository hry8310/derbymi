/*

   Derby - Class org.apache.derby.impl.sql.compile.DeleteNode

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
import java.util.List;
import java.util.Properties;

import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.services.io.FormatableProperties;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.StatementType;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptor;
import org.apache.derby.iapi.sql.dictionary.ColumnDescriptorList;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.dictionary.TriggerDescriptor;
import org.apache.derby.iapi.sql.dictionary.TriggerDescriptorList;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.iapi.store.access.StaticCompiledOpenConglomInfo;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.util.ReuseFactory;
import org.apache.derby.vti.DeferModification;


/**
 * A DeleteNode represents a DELETE statement. It is the top-level node
 * for the statement.
 *
 * For positioned delete, there may be no from table specified.
 * The from table will be derived from the cursor specification of
 * the named cursor.
 *
 */
class DeleteNode extends DMLModStatementNode
{
	/* Column name for the RowLocation column in the ResultSet */
	private static final String COLUMNNAME = "###RowLocationToDelete";

	/* Filled in by bind. */
    private boolean deferred;
    private FromTable targetTable;
    private FormatableBitSet readColsBitSet;

	private ConstantAction[] dependentConstantActions;
	private boolean cascadeDelete;
	private StatementNode[] dependentNodes;

	/**
     * Constructor for a DeleteNode.
	 *
	 * @param targetTableName	The name of the table to delete from
	 * @param queryExpression	The query expression that will generate
	 *				the rows to delete from the given table
     * @param matchingClause   Non-null if this DML is part of a MATCHED clause of a MERGE statement.
     * @param cm                The context manager
	 */

    DeleteNode
        (
         TableName targetTableName,
         ResultSetNode queryExpression,
         MatchingClauseNode matchingClause,
         ContextManager cm
         )
    {
        super( queryExpression, matchingClause, cm );
        this.targetTableName = targetTableName;
	}

    @Override
    String statementToString()
	{
		return "DELETE";
	}

	 

    @Override
	int getPrivType()
	{
		return Authorizer.DELETE_PRIV;
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
		//If delete table is on a SESSION schema table, then return true. 
		return resultSet.referencesSessionSchema();
	}

	/**
	 * Compile constants that Execution will use
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
    public ConstantAction makeConstantAction() throws StandardException
	{

		/* Different constant actions for base tables and updatable VTIs */
		if (targetTableDescriptor != null)
		{
			// Base table
            int lckMode = resultSet.updateTargetLockMode();
			long heapConglomId = targetTableDescriptor.getHeapConglomerateId();
			TransactionController tc = getLanguageConnectionContext().getTransactionCompile();
			StaticCompiledOpenConglomInfo[] indexSCOCIs = 
				new StaticCompiledOpenConglomInfo[indexConglomerateNumbers.length];

			for (int index = 0; index < indexSCOCIs.length; index++)
			{
				indexSCOCIs[index] = tc.getStaticCompiledConglomInfo(indexConglomerateNumbers[index]);
			}

			/*
			** Do table locking if the table's lock granularity is
			** set to table.
			*/
			if (targetTableDescriptor.getLockGranularity() == TableDescriptor.TABLE_LOCK_GRANULARITY)
			{
                lckMode = TransactionController.MODE_TABLE;
			}

			ResultDescription resultDescription = null;
			if(isDependentTable)
			{
				//triggers need the result description ,
				//dependent tables  don't have a source from generation time
				//to get the result description
				resultDescription = makeResultDescription();
			}


			return	getGenericConstantActionFactory().getDeleteConstantAction
				( heapConglomId,
				  targetTableDescriptor.getTableType(),
				  tc.getStaticCompiledConglomInfo(heapConglomId),
				  indicesToMaintain,
				  indexConglomerateNumbers,
				  indexSCOCIs,
				  deferred,
				  false,
				  targetTableDescriptor.getUUID(),
                  lckMode,
				  null, null, null, 0, null, null, 
				  resultDescription,
				  getFKInfo(), 
				  getTriggerInfo(), 
				  (readColsBitSet == null) ? (FormatableBitSet)null : new FormatableBitSet(readColsBitSet),
				  getReadColMap(targetTableDescriptor.getNumberOfColumns(),readColsBitSet),
				  resultColumnList.getStreamStorableColIds(targetTableDescriptor.getNumberOfColumns()),
 				  (readColsBitSet == null) ? 
					  targetTableDescriptor.getNumberOfColumns() :
					  readColsBitSet.getNumBitsSet(),			
				  (UUID) null,
				  resultSet.isOneRowResultSet(),
				  dependentConstantActions,
                  inMatchingClause());
		}
		else
		{
			/* Return constant action for VTI
			 * NOTE: ConstantAction responsible for preserving instantiated
			 * VTIs for in-memory queries and for only preserving VTIs
			 * that implement Serializable for SPSs.
			 */
			return	getGenericConstantActionFactory().getUpdatableVTIConstantAction( DeferModification.DELETE_STATEMENT,
						deferred);
		}
	}

	 

	/**
	 * Return the type of statement, something from
	 * StatementType.
	 *
	 * @return the type of statement
	 */
    @Override
	protected final int getStatementType()
	{
		return StatementType.DELETE;
	}


	/**
	  *	Gets the map of all columns which must be read out of the base table.
	  * These are the columns needed to:
	  *
	  *		o	maintain indices
	  *		o	maintain foreign keys
	  *
	  *	The returned map is a FormatableBitSet with 1 bit for each column in the
	  * table plus an extra, unsued 0-bit. If a 1-based column id must
	  * be read from the base table, then the corresponding 1-based bit
	  * is turned ON in the returned FormatableBitSet.
	  *
	  *	@param	dd				the data dictionary to look in
	  *	@param	baseTable		the base table descriptor
	  *
	  *	@return	a FormatableBitSet of columns to be read out of the base table
	  *
	  * @exception StandardException		Thrown on error
	  */
	public	FormatableBitSet	getReadMap
	(
		DataDictionary		dd,
		TableDescriptor		baseTable
	)
		throws StandardException
	{
		boolean[]	needsDeferredProcessing = new boolean[1];
		needsDeferredProcessing[0] = requiresDeferredProcessing();

        ArrayList<ConglomerateDescriptor> conglomerates = new ArrayList<ConglomerateDescriptor>();
        relevantTriggers = new TriggerDescriptorList();

        FormatableBitSet columnMap = DeleteNode.getDeleteReadMap(baseTable,
                conglomerates, relevantTriggers, needsDeferredProcessing);

        markAffectedIndexes(conglomerates);

		adjustDeferredFlag( needsDeferredProcessing[0] );

		return	columnMap;
	}

	/**
	 * In case of referential actions, we require to perform
	 * DML (UPDATE or DELETE) on the dependent tables. 
	 * Following function returns the DML Node for the dependent table.
	 */
	private StatementNode getDependentTableNode(String tableName, int refAction,
												ColumnDescriptorList cdl) throws StandardException
	{
        DMLModStatementNode node = null;

		int index = tableName.indexOf('.');
		String schemaName = tableName.substring(0 , index);
		String tName = tableName.substring(index+1);
		if(refAction == StatementType.RA_CASCADE)
		{
			node = getEmptyDeleteNode(schemaName , tName);
		}

		if(refAction == StatementType.RA_SETNULL)
		{
			node = getEmptyUpdateNode(schemaName , tName, cdl);
		}

        // The dependent node should be marked as such, and it should inherit
        // the set of dependent tables from the parent so that it can break
        // out of cycles in the dependency graph.
        if (node != null) {
            node.isDependentTable = true;
            node.dependentTables = dependentTables;
        }

		return node;
	}


    private DeleteNode getEmptyDeleteNode(String schemaName, String targetTableName)
        throws StandardException
    {
        ValueNode whereClause = null;

        TableName tableName =
            new TableName(schemaName , targetTableName, getContextManager());

        FromList fromList = new FromList(getContextManager());

        FromTable fromTable = new FromBaseTable(
                tableName,
                null,
                FromBaseTable.DELETE,
                null,
                getContextManager());

		//we would like to use references index & table scan instead of 
		//what optimizer says for the dependent table scan.
		Properties targetProperties = new FormatableProperties();
		targetProperties.put("index", "null");
		((FromBaseTable) fromTable).setTableProperties(targetProperties);

        fromList.addFromTable(fromTable);
        SelectNode rs = new SelectNode(null,
                                       fromList, /* FROM list */
                                       whereClause, /* WHERE clause */
                                       null, /* GROUP BY list */
                                       null, /* having clause */
                                       null, /* windows */
                                       null, /* optimizer override plan */
                                       getContextManager());

        return new DeleteNode(tableName, rs, null, getContextManager());
    }


	
    private UpdateNode getEmptyUpdateNode(String schemaName,
											 String targetTableName,
											 ColumnDescriptorList cdl)
        throws StandardException
    {

        ValueNode whereClause = null;

        TableName tableName =
            new TableName(schemaName , targetTableName, getContextManager());

        FromList fromList = new FromList(getContextManager());

        FromTable fromTable = new FromBaseTable(
                tableName,
                null,
                ReuseFactory.getInteger(FromBaseTable.DELETE),
                null,
                getContextManager());


		//we would like to use references index & table scan instead of 
		//what optimizer says for the dependent table scan.
		Properties targetProperties = new FormatableProperties();
		targetProperties.put("index", "null");
		((FromBaseTable) fromTable).setTableProperties(targetProperties);

        fromList.addFromTable(fromTable);

        SelectNode sn = new SelectNode(getSetClause(cdl),
                                              fromList, /* FROM list */
                                              whereClause, /* WHERE clause */
                                              null, /* GROUP BY list */
                                              null, /* having clause */
                                              null, /* windows */
                                              null, /* optimizer override plan */
                                              getContextManager());

        return new UpdateNode(tableName, sn, null, getContextManager());
    }


 
    private ResultColumnList getSetClause(ColumnDescriptorList cdl)
		throws StandardException
	{
		ResultColumn resultColumn;
		ValueNode	 valueNode;

        ResultColumnList columnList = new ResultColumnList(getContextManager());

        valueNode = new UntypedNullConstantNode(getContextManager());
		for(int index =0 ; index < cdl.size() ; index++)
		{
            ColumnDescriptor cd = cdl.elementAt(index);
			//only columns that are nullable need to be set to 'null' for ON
			//DELETE SET NULL
			if((cd.getType()).isNullable())
			{
                resultColumn =
                        new ResultColumn(cd, valueNode, getContextManager());

				columnList.addResultColumn(resultColumn);
			}
		}
		return columnList;
	}

   

    /**
	  *	Builds a bitmap of all columns which should be read from the
	  *	Store in order to satisfy an DELETE statement.
	  *
	  *
	  *	1)	finds all indices on this table
	  *	2)	adds the index columns to a bitmap of affected columns
	  *	3)	adds the index descriptors to a list of conglomerate
	  *		descriptors.
	  *	4)	finds all DELETE triggers on the table
	  *	5)	if there are any DELETE triggers, then do one of the following
	  *     a)If all of the triggers have MISSING referencing clause, then that
	  *      means that the trigger actions do not have access to before and
	  *      after values. In that case, there is no need to blanketly decide 
	  *      to include all the columns in the read map just because there are
	  *      triggers defined on the table.
	  *     b)Since one/more triggers have REFERENCING clause on them, get all
	  *      the columns because we don't know what the user will ultimately 
	  *      reference.
	  *	6)	adds the triggers to an evolving list of triggers
	  *
      * @param  conglomerates       OUT: list of affected indices
	  *	@param	relevantTriggers	IN/OUT. Passed in as an empty list. Filled in as we go.
	  *	@param	needsDeferredProcessing			IN/OUT. true if the statement already needs
	  *											deferred processing. set while evaluating this
	  *											routine if a trigger requires
	  *											deferred processing
	  *
	  * @return a FormatableBitSet of columns to be read out of the base table
	  *
	  * @exception StandardException		Thrown on error
	  */
	private static FormatableBitSet getDeleteReadMap
	(
		TableDescriptor				baseTable,
        List<ConglomerateDescriptor>  conglomerates,
        TriggerDescriptorList       relevantTriggers,
		boolean[]					needsDeferredProcessing
	)
		throws StandardException
	{
		int		columnCount = baseTable.getMaxColumnID();
		FormatableBitSet	columnMap = new FormatableBitSet(columnCount + 1);

		/* 
		** Get a list of the indexes that need to be 
		** updated.  ColumnMap contains all indexed
		** columns where 1 or more columns in the index
		** are going to be modified.
		**
		** Notice that we don't need to add constraint
		** columns.  This is because we add all key constraints
		** (e.g. foreign keys) as a side effect of adding their
		** indexes above.  And we don't need to deal with
		** check constraints on a delete.
		**
		** Adding indexes also takes care of the replication 
		** requirement of having the primary key.
		*/
        DMLModStatementNode.getXAffectedIndexes(
                baseTable, null, columnMap, conglomerates);

		/*
	 	** If we have any DELETE triggers, then do one of the following
	 	** 1)If all of the triggers have MISSING referencing clause, then that
	 	** means that the trigger actions do not have access to before and 
	 	** after values. In that case, there is no need to blanketly decide to
	 	** include all the columns in the read map just because there are
	 	** triggers defined on the table.
	 	** 2)Since one/more triggers have REFERENCING clause on them, get all 
	 	** the columns because we don't know what the user will ultimately reference.
	 	*/
		baseTable.getAllRelevantTriggers( StatementType.DELETE, (int[])null, relevantTriggers );

		if (relevantTriggers.size() > 0)
		{
			needsDeferredProcessing[0] = true;
			
			boolean needToIncludeAllColumns = false;

            for (TriggerDescriptor trd : relevantTriggers) {
				//Does this trigger have REFERENCING clause defined on it.
				//If yes, then read all the columns from the trigger table.
				if (!trd.getReferencingNew() && !trd.getReferencingOld())
					continue;
				else
				{
					needToIncludeAllColumns = true;
					break;
				}
			}

			if (needToIncludeAllColumns) {
				for (int i = 1; i <= columnCount; i++)
				{
					columnMap.set(i);
				}
			}
		}

		return	columnMap;
	}
    
	/*
	 * Force column references (particularly those added by the compiler)
	 * to use the correlation name on the base table, if any.
	 */
	private	void	correlateAddedColumns( ResultColumnList rcl, FromTable fromTable )
		throws StandardException
	{
		String		correlationName = fromTable.getCorrelationName();

		if ( correlationName == null ) { return; }

		TableName	correlationNameNode = makeTableName( null, correlationName );

        for (ResultColumn column : rcl)
		{
			ValueNode		expression = column.getExpression();

			if ( (expression != null) && (expression instanceof ColumnReference) )
			{
				ColumnReference	reference = (ColumnReference) expression;
				
				reference.setQualifiedTableName( correlationNameNode );
			}
		}
		
	}
	
}
