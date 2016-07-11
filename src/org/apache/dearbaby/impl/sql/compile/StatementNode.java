/*

   Derby - Class org.apache.derby.impl.sql.compile.StatementNode

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

import java.util.HashMap;

import org.apache.dearbaby.util.QueryUtil;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.ResultDescription;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor; 
import org.apache.derby.shared.common.sanity.SanityManager;

/**
 * A StatementNode represents a single statement in the language.  It is
 * the top node for any statement.
 * <p>
 * StatementNode controls the class generation for query tree nodes.
 *
 */

/*
* History:
*	5/8/97	Rick Hilleags	Moved node-name-string to child classes.
*/

public abstract class StatementNode extends QueryTreeNode
{
    /** Cached empty list object. */
    static final TableDescriptor[] EMPTY_TD_LIST = new TableDescriptor[0];

    StatementNode(ContextManager cm) {
        super(cm);
    }

    public ResultColumnList getCols(){
    	return null;
    }
    
   
	/**
	 * By default, assume StatementNodes are atomic.
	 * The rare statements that aren't atomic (e.g.
	 * CALL method()) override this.
	 *
	 * @return true if the statement is atomic
	 *
	 * @exception StandardException		Thrown on error
	 */	
    @Override
	public boolean isAtomic() throws StandardException
	{
		return true;
	}
	
	/**
	 * Returns whether or not this Statement requires a set/clear savepoint
	 * around its execution.  The following statement "types" do not require them:
	 *		Cursor	- unnecessary and won't work in a read only environment
	 *		Xact	- savepoint will get blown away underneath us during commit/rollback
	 * <p>
	 * ONLY CALLABLE AFTER GENERATION
	 * <P>
	 * This implementation returns true, sub-classes can override the
	 * method to not require a savepoint.
	 *
	 * @return boolean	Whether or not this Statement requires a set/clear savepoint
	 */
	public boolean needsSavepoint()
	{
		return true;
	}
	
	/**
	 * Get the name of the SPS that is used to execute this statement. Only
	 * relevant for an ExecSPSNode -- otherwise, returns null.
	 * 
	 * @return the name of the underlying sps
	 */
	public String getSPSName() {
		return null;
	}

	/**
	 * Returns the name of statement in EXECUTE STATEMENT command. Returns null
	 * for all other commands.
	 * 
	 * @return String null unless overridden for Execute Statement command
	 */
	public String executeStatementName() {
		return null;
	}

	/**
	 * Returns name of schema in EXECUTE STATEMENT command. Returns null for all
	 * other commands.
	 * 
	 * @return String schema for EXECUTE STATEMENT null for all others
	 */
	public String executeSchemaName() {
		return null;
	}
	
	/**
	 * Only DML statements have result descriptions - for all others return
	 * null. This method is overridden in DMLStatementNode.
	 * 
	 * @return null
	 * 
	 */
	public ResultDescription makeResultDescription() {
		return null;
	}

    /**
     * Get an object with information about the cursor if there is one.
     */
    public Object getCursorInfo() throws StandardException {
        return null;
    }

	/**
	 * Convert this object to a String. See comments in QueryTreeNode.java for
	 * how this should be done for tree printing.
	 * 
	 * @return This object as a String
	 */
    @Override
	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return "statementType: " + statementToString() + "\n" +
				super.toString();
		}
		else
		{
			return "";
		}
	}

    abstract String statementToString();
	
 

	/**
	 * create the outer shell class builder for the class we will
	 * be generating, generate the expression to stuff in it,
	 * and turn it into a class.
	 */
	static final int NEED_DDL_ACTIVATION = 5;
	static final int NEED_CURSOR_ACTIVATION = 4;
	static final int NEED_PARAM_ACTIVATION = 2;
	static final int NEED_ROW_ACTIVATION = 1;
	static final int NEED_NOTHING_ACTIVATION = 0;

	abstract int activationKind();

	/* We need to get some kind of table lock (IX here) at the beginning of
	 * compilation of DMLModStatementNode and DDLStatementNode, to prevent the
	 * interference of insert/update/delete/DDL compilation and DDL execution,
	 * see beetle 3976, 4343, and $WS/language/SolutionsToConcurrencyIssues.txt
	 */
	protected TableDescriptor lockTableForCompilation(TableDescriptor td)
		throws StandardException
	{
		DataDictionary dd = getDataDictionary();

		/* we need to lock only if the data dictionary is in DDL cache mode
		 */
		if (dd.getCacheMode() == DataDictionary.DDL_MODE)
		{ }
		return td;
	}

 

    /**
     * Returns a list of base tables for which the index statistics of the
     * associated indexes should be updated.
     * <p>
     * This default implementation always returns an empty list.
     *
     * @return A list of table descriptors (potentially empty).
     * @throws StandardException if accessing the index descriptors of a base
     *      table fails
     */
    public TableDescriptor[] updateIndexStatisticsFor()
            throws StandardException {
        // Do nothing, overridden by appropriate nodes.
        return EMPTY_TD_LIST;
    }
}
