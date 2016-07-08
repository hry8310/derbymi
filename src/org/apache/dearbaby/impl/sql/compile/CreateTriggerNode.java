/*

   Derby - Class org.apache.derby.impl.sql.compile.CreateTriggerNode

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
import java.util.Comparator;
import java.util.List;

import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.iapi.sql.depend.ProviderInfo;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.sql.execute.ConstantAction;
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A CreateTriggerNode is the root of a QueryTree 
 * that represents a CREATE TRIGGER
 * statement.
 *
 */

class CreateTriggerNode extends DDLStatementNode
{
	private	TableName			triggerName;
	private	TableName			tableName;
	private	int					triggerEventMask;
	private ResultColumnList	triggerCols;
	private	boolean				isBefore;
	private	boolean				isRow;
	private	boolean				isEnabled;
    private List<TriggerReferencingStruct> refClause;
	private	ValueNode		    whenClause;
	private	String				whenText;
	private	StatementNode		actionNode;
	private	String				actionText;
    private String              originalWhenText;
    private String              originalActionText;
    private ProviderInfo[]      providerInfo;

	private SchemaDescriptor	triggerSchemaDescriptor;
	private SchemaDescriptor	compSchemaDescriptor;
	
	/*
	 * The following arrary will include columns that will cause the trigger to
	 * fire. This information will get saved in SYSTRIGGERS.
	 * 
	 * The array will be null for all kinds of insert and delete triggers but
	 * it will be non-null for a subset of update triggers.
	 *  
	 * For update triggers, the array will be null if no column list is 
	 * supplied in the CREATE TRIGGER trigger column clause as shown below.
	 * The UPDATE trigger below will fire no matter which column in table1
	 * gets updated.
	 * eg
	 * CREATE TRIGGER tr1 AFTER UPDATE ON table1 
	 *    REFERENCING OLD AS oldt NEW AS newt
	 *    FOR EACH ROW UPDATE table2 SET c24=oldt.c14;
	 * 
	 * For update triggers, this array will be non-null if specific trigger
	 * column(s) has been specified in the CREATE TRIGGER sql. The UPDATE
	 * trigger below will fire when an update happens on column c12 in table1.
	 * eg
	 * CREATE TRIGGER tr1 AFTER UPDATE OF c12 ON table1 
	 *    REFERENCING OLD AS oldt NEW AS newt
	 *    FOR EACH ROW UPDATE table2 SET c24=oldt.c14;
	 * 
	 * Array referencedColInts along with referencedColsInTriggerAction will 
	 * be used to determine which columns from the triggering table need to 
	 * be read in when the trigger fires, thus making sure that we do not
	 * read the columns from the trigger table that are not required for
	 * trigger execution.
	 */
	private int[]				referencedColInts;
	
	/*
	 * The following array (which was added as part of DERBY-1482) will 
	 * include columns referenced in the trigger action through the 
	 * REFERENCING clause(old/new transition variables), in other trigger
	 * action columns. This information will get saved in SYSTRIGGERS
	 * (with the exception of triggers created in pre-10.7 dbs. For 
	 * pre-10.7 dbs, this information will not get saved in SYSTRIGGERS
	 * in order to maintain backward compatibility.
	 * 
	 * Unlike referencedColInts, this array can be non-null for all 3 types
	 * of triggers, namely, INSERT, UPDATE AND DELETE triggers. This array
	 * will be null if no columns in the trigger action are referencing
	 * old/new transition variables
	 * 
	 * eg of a trigger in 10.7 and higher dbs which will cause 
	 * referencedColsInTriggerAction to be null
	 * CREATE TRIGGER tr1 NO CASCADE BEFORE UPDATE of c12 ON table1
	 *    SELECT c24 FROM table2 WHERE table2.c21 = 1
	 * 
	 * eg of a trigger in 10.7 and higher dbs which will cause 
	 * referencedColsInTriggerAction to be non-null
	 * For the trigger below, old value of column c14 from trigger table is
	 * used in the trigger action through old/new transition variables. A
	 * note of this requirement to read c14 will be made in
	 * referencedColsInTriggerAction array.
	 * eg
	 * CREATE TRIGGER tr1 AFTER UPDATE ON table1 
	 *    REFERENCING OLD AS oldt NEW AS newt
	 *    FOR EACH ROW UPDATE table2 SET c24=oldt.c14;
	 * 
	 * The exception to the rules above for trigger action columns information
	 * in referencedColsInTriggerAction is a trigger that was created with
	 * pre-10.7 release. Prior to 10.7, we did not collect any information
	 * about trigger action columns. So, any of the 2 kinds of trigger shown
	 * above prior to 10.7 will not have any trigger action column info on
	 * them in SYSTRIGGERS table. In order to cover the pre-existing pre-10.7
	 * triggers and all the other kinds of triggers, we will follow following
	 * 4 rules during trigger execution.
	 *   Rule1)If trigger column information is null, then read all the
	 *   columns from trigger table into memory irrespective of whether
	 *   there is any trigger action column information. 2 egs of such
	 *   triggers
	 *      create trigger tr1 after update on t1 for each row values(1);
	 *      create trigger tr1 after update on t1 referencing old as oldt
	 *      	for each row insert into t2 values(2,oldt.j,-2);
	 *   Rule2)If trigger column information is available but no trigger
	 *   action column information is found and no REFERENCES clause is
	 *   used for the trigger, then only read the columns identified by
	 *   the trigger column. eg
	 *      create trigger tr1 after update of c1 on t1 
	 *      	for each row values(1);
	 *   Rule3)If trigger column information and trigger action column
	 *   information both are not null, then only those columns will be
	 *   read into memory. This is possible only for triggers created in
	 *   release 10.7 or higher. Because prior to that we did not collect
	 *   trigger action column informatoin. eg
	 *      create trigger tr1 after update of c1 on t1
	 *      	referencing old as oldt for each row
	 *      	insert into t2 values(2,oldt.j,-2);
	 *   Rule4)If trigger column information is available but no trigger
	 *   action column information is found but REFERENCES clause is used
	 *   for the trigger, then read all the columns from the trigger
	 *   table. This will cover soft-upgrade and hard-upgrade scenario
	 *   for triggers created pre-10.7. This rule prevents us from having
	 *   special logic for soft-upgrade. Additionally, this logic makes
	 *   invalidation of existing triggers unnecessary during
	 *   hard-upgrade. The pre-10.7 created triggers will work just fine
	 *   even though for some triggers, they would have trigger action
	 *   columns missing from SYSTRIGGERS. A user can choose to drop and
	 *   recreate such triggers to take advantage of Rule 3 which will
	 *   avoid unnecessary column reads during trigger execution.
	 *   eg trigger created prior to 10.7
	 *      create trigger tr1 after update of c1 on t1
	 *      	referencing old as oldt for each row
	 *      	insert into t2 values(2,oldt.j,-2);
	 *   To reiterate, Rule4) is there to cover triggers created with
	 *   pre-10,7 releases but now that database has been
	 *   hard/soft-upgraded to 10.7 or higher version. Prior to 10.7,
	 *   we did not collect any information about trigger action columns.
	 *   
	 *   The only place we will need special code for soft-upgrade is during
	 *   trigger creation. If we are in soft-upgrade mode, we want to make sure
	 *   that we do not save information about trigger action columns in
	 *   SYSTRIGGERS because the releases prior to 10.7 do not understand
	 *   trigger action column information.
	 *   
	 * Array referencedColInts along with referencedColsInTriggerAction will 
	 * be used to determine which columns from the triggering table needs to 
	 * be read in when the trigger fires, thus making sure that we do not
	 * read the columns from the trigger table that are not required for
	 * trigger execution.
	 */
	private int[]				referencedColsInTriggerAction;
	private TableDescriptor		triggerTableDescriptor;

	/*
	** Names of old and new table.  By default we have
	** OLD/old and NEW/new.  The casing is dependent on 
	** the language connection context casing as the rest
    ** of other code. Therefore we will set the value of the 
    ** String at the init() time.
    ** However, if there is a referencing clause
	** we will reset these values to be whatever the user
	** wants.
	*/
	private String oldTableName;
	private String newTableName;

	private boolean oldTableInReferencingClause;
	private boolean newTableInReferencingClause;

    /**
     * <p>
     * A list that describes how the original SQL text of the trigger action
     * statement was modified when transition tables and transition variables
     * were replaced by VTI calls. Each element in the list contains four
     * integers describing positions where modifications have happened. The
     * first two integers are begin and end positions of a transition table
     * or transition variable in {@link #originalActionText the original SQL
     * text}. The last two integers are begin and end positions of the
     * corresponding replacement in {@link #actionText the transformed SQL
     * text}.
     * </p>
     *
     * <p>
     * Begin positions are inclusive and end positions are exclusive.
     * </p>
     */
    private final ArrayList<int[]>
            actionTransformations = new ArrayList<int[]>();

    /**
     * Structure that has the same shape as {@code actionTransformations},
     * except that it describes the transformations in the WHEN clause.
     */
    private final ArrayList<int[]>
            whenClauseTransformations = new ArrayList<int[]>();

	/**
     * Constructor for a CreateTriggerNode
	 *
	 * @param triggerName			name of the trigger	
	 * @param tableName				name of the table which the trigger is declared upon	
	 * @param triggerEventMask		TriggerDescriptor.TRIGGER_EVENT_XXX
	 * @param triggerCols			columns trigger is to fire upon.  Valid
	 *								for UPDATE case only.
	 * @param isBefore				is before trigger (false for after)
	 * @param isRow					true for row trigger, false for statement
	 * @param isEnabled				true if enabled
	 * @param refClause				the referencing clause
	 * @param whenClause			the WHEN clause tree
	 * @param whenText				the text of the WHEN clause
	 * @param actionNode			the trigger action tree
	 * @param actionText			the text of the trigger action
     * @param cm                    context manager
	 *
	 * @exception StandardException		Thrown on error
	 */
    CreateTriggerNode
	(
        TableName       triggerName,
        TableName       tableName,
        int             triggerEventMask,
        ResultColumnList triggerCols,
        boolean         isBefore,
        boolean         isRow,
        boolean         isEnabled,
        List<TriggerReferencingStruct> refClause,
        ValueNode       whenClause,
        String          whenText,
        StatementNode   actionNode,
        String          actionText,
        ContextManager  cm
	) throws StandardException
	{
        super(triggerName, cm);

        this.triggerName = triggerName;
        this.tableName = tableName;
        this.triggerEventMask = triggerEventMask;
        this.triggerCols = triggerCols;
        this.isBefore = isBefore;
        this.isRow = isRow;
        this.isEnabled = isEnabled;
        this.refClause = refClause;
        this.whenClause = whenClause;
        this.originalWhenText = whenText;
        this.whenText = (whenText == null) ? null : whenText.trim();
        this.actionNode = actionNode;
        this.originalActionText = actionText;
        this.actionText = (actionText == null) ? null : actionText.trim();
        this.implicitCreateSchema = true;
	}

    String statementToString()
	{
		return "CREATE TRIGGER";
	}

	/**
	 * Prints the sub-nodes of this object.  See QueryTreeNode.java for
	 * how tree printing is supposed to work.
	 *
	 * @param depth		The depth of this node in the tree
	 */
    @Override
    void printSubNodes(int depth)
	{
		if (SanityManager.DEBUG)
		{
			super.printSubNodes(depth);

			if (triggerCols != null)
			{
				printLabel(depth, "triggerColumns: ");
				triggerCols.treePrint(depth + 1);
			}
			if (whenClause != null)
			{
				printLabel(depth, "whenClause: ");
				whenClause.treePrint(depth + 1);
			}
			if (actionNode != null)
			{
				printLabel(depth, "actionNode: ");
				actionNode.treePrint(depth + 1);
			}
		}
	}


	// accessors


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
		//If create trigger is part of create statement and the trigger is defined on or it references SESSION schema tables,
		//it will get caught in the bind phase of trigger and exception will be thrown by the trigger bind. 
        return isSessionSchema(triggerTableDescriptor.getSchemaName())
                || actionNode.referencesSessionSchema()
                || (whenClause != null && whenClause.referencesSessionSchema());
	}

    /**
     * Comparator that can be used for sorting lists of FromBaseTables
     * on the position they have in the SQL query string.
     */
    private static final Comparator<FromBaseTable> OFFSET_COMPARATOR = new Comparator<FromBaseTable>() {
        public int compare(FromBaseTable o1, FromBaseTable o2) {
            // Return negative int, zero, or positive int if the offset of the
            // first table is less than, equal to, or greater than the offset
            // of the second table.
            return o1.getTableNameField().getBeginOffset() -
                    o2.getTableNameField().getBeginOffset();
        }
    };

	 
 
        
	  

	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
    @Override
	public ConstantAction makeConstantAction() throws StandardException
	{
		String oldReferencingName = (oldTableInReferencingClause) ? oldTableName : null;
		String newReferencingName = (newTableInReferencingClause) ? newTableName : null;

		return	getGenericConstantActionFactory().getCreateTriggerConstantAction(
											triggerSchemaDescriptor.getSchemaName(),
											getRelativeName(),
											triggerEventMask,
											isBefore,
											isRow,
											isEnabled,
											triggerTableDescriptor,	
											(UUID)null,			// when SPSID
											whenText,
											(UUID)null,			// action SPSid 
											actionText,
                                            compSchemaDescriptor.getUUID(),
											referencedColInts,
											referencedColsInTriggerAction,
                                            originalWhenText,
											originalActionText,
											oldTableInReferencingClause,
											newTableInReferencingClause,
											oldReferencingName,
                                            newReferencingName,
                                            providerInfo
											);
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
			String refString = "null";
			if (refClause != null)
			{
                StringBuilder buf = new StringBuilder();
                for (TriggerReferencingStruct trn : refClause)
				{
					buf.append("\t");
					buf.append(trn.toString());
					buf.append("\n");
				}
				refString = buf.toString();
			}

			return super.toString() +
				"tableName: "+tableName+		
				"\ntriggerEventMask: "+triggerEventMask+		
				"\nisBefore: "+isBefore+		
				"\nisRow: "+isRow+		
				"\nisEnabled: "+isEnabled+		
				"\nwhenText: "+whenText+
				"\nrefClause: "+refString+
				"\nactionText: "+actionText+
				"\n";
		}
		else
		{
			return "";
		}
	}

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (triggerName != null) {
            triggerName = (TableName) triggerName.accept(v);
        }

        if (tableName != null) {
            tableName = (TableName) tableName.accept(v);
        }
    }
}
