/*

   Derby - Class org.apache.derby.iapi.sql.Activation

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

package org.apache.derby.iapi.sql;

import java.sql.SQLWarning;
import java.util.Enumeration;
import java.util.Vector;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.SQLSessionContext; 
import org.apache.derby.iapi.sql.dictionary.TableDescriptor; 
import org.apache.derby.iapi.types.DataTypeDescriptor;
import org.apache.derby.iapi.types.DataValueFactory;
import org.apache.derby.iapi.types.NumberDataValue;
import org.apache.derby.iapi.types.RowLocation;


/**
 * An activation contains all the local state information necessary
 * to execute a re-entrant PreparedStatement. The way it will actually work
 * is that a PreparedStatement will have an executable plan, which will be
 * a generated class. All of the local state will be variables in the class.
 * Creating a new instance of the executable plan will create the local state
 * variables. This means that an executable plan must implement this interface,
 * and that the PreparedStatement.getActivation() method will do a
 * "new" operation on the executable plan.
 * <p>
 * The fixed implementations of Activation in the Execution impl
 * package are used as skeletons for the classes generated for statements
 * when they are compiled.
 * <p>
 * There are no fixed implementations of Activation for statements;
 * a statement has an activation generated for it when it is compiled.
 *
 */

public interface Activation  
{
	/**
	 * Resets the activation to the "pre-execution" state -
	 * that is, the state where it can be used to begin a new execution.
	 * Frees local buffers, stops scans, resets counters to zero, sets
	 * current date and time to an unitialized state, etc.
	 *
	 * @exception StandardException thrown on failure
	 */
	void	reset() throws StandardException;

	/**
	 * JDBC requires that all select statements be converted into cursors,
	 * and that the cursor name be settable for each execution of a select
	 * statement. The Language Module will support this, so that the JDBC
	 * driver will not have to parse JSQL text. This method will have no
	 * effect when called on non-select statements.
	 * <p>
	 * There will be a JSQL statement to disable the "cursorization" of
	 * all select statements. For non-cursorized select statements, this
	 * method will have no effect.
	 * <p>
	 * This has no effect if the activation has been closed.
	 * <p>
	 * @param cursorName  The cursor name to use.
	 */
	void	setCursorName(String cursorName);

	/**
	 * Temporary tables can be declared with ON COMMIT DELETE ROWS. But if the table has a held curosr open at
	 * commit time, data should not be deleted from the table. This method, (gets called at commit time) checks if this
	 * activation held cursor and if so, does that cursor reference the passed temp table name.
	 *
	 * @return	true if this activation has held cursor and if it references the passed temp table name
	 */
	public boolean checkIfThisActivationHasHoldCursor(String tableName);

	/**
	 * Gets the ParameterValueSet for this execution of the statement.
	 *
	 * @return	The ParameterValueSet for this execution of the
	 *		statement. Returns NULL if there are no parameters.
	 */
	ParameterValueSet	getParameterValueSet();

	/**
	 * Sets the parameter values for this execution of the statement.
	 * <p>
	 * Has no effect if the activation has been closed.
	 *
	 * <p>
	 * NOTE: The setParameters() method is currently unimplemented. 
	 * A statement with parameters will generate its own ParameterValueSet,
	 * which can be gotten with the getParameterValueSet() method (above).
	 * The idea behind setParameters() is to improve performance when
	 * operating across a network by allowing all the parameters to be set
	 * in one call, as opposed to one call per parameter.
	 *
	 * @param parameterValues	The values of the parameters.
	 */
	void	setParameters(ParameterValueSet parameterValues, DataTypeDescriptor[] parameterTypes) throws StandardException;

	/**
	 * When the prepared statement is executed, it passes
	 * execution on to the activation execution was requested for.
	 *
	 * @return the ResultSet for further manipulation, if any.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	ResultSet execute() throws StandardException;

	/**
		Closing an activation statement marks it as unusable. Any other
		requests made on it will fail.  An activation should be
		marked closed when it is expected to not be used any longer,
		i.e. when the connection for it is closed, or it has suffered some
		sort of severe error. This will also close its result set and
		release any resources it holds e.g. for parameters.
		<P>
		Any class that implements this must be prepared to be executed
		from garbage collection, ie. there is no matching context stack.

		@exception StandardException		Thrown on failure
	 */
	void close() throws StandardException;

	/**
		Find out if the activation is closed or not.

		@return true if the Activation has been closed.
	 */
	boolean isClosed();

	/**
		Set this Activation for a single execution.
		E.g. a java.sql.Statement execution.
	*/
	void setSingleExecution();

	/**
		Returns true if this Activation is only going to be used for
		one execution.
	*/
	boolean isSingleExecution();

	/**
	  Returns the chained list of warnings. Returns null
	  if there are no warnings.
	  */
	SQLWarning getWarnings();

	/**
	  Add a warning to the activation
	  */
	void addWarning(SQLWarning w);

	/**
	  Clear the activation's warnings.
	  */
	void clearWarnings();

	/**
	 * Get the language connection context associated with this activation
     */
	public	LanguageConnectionContext	getLanguageConnectionContext();

 
	/**
	 * Returns the current result set for this activation, i.e.
	 * the one returned by the last execute() call.  If there has
	 * been no execute call or the activation has been reset or closed,
	 * a null is returned.
	 *
	 * @return the current ResultSet of this activation.
	 */
	ResultSet getResultSet();

	 

	/**
	 * Get the current row at the given index.
	 */
	public Row getCurrentRow(int resultSetNumber);
    
	/**
	 * Generated plans have a current row field for ease in defining
	 * the methods and finding them dynamically. The interface is
	 * used to set the row before a dynamic method that uses it is
	 * invoked.
	 * <p>
	 * When all processing on the currentRow has been completed,
	 * callers should call activation.clearCurrentRow(resultSetNumber)
	 * to ensure that no unnecessary references are retained to rows.
	 * This will allow the rows no longer in use to be collected by
	 * the garbage collecter.
	 *
	 * @param resultSetNumber	The resultSetNumber for the current ResultSet
	 */
	/* RESOLVE - this method belongs on an internal, not external, interface */
	void clearCurrentRow(int resultSetNumber);

	 
	/**
		Check the validity of the current executing statement. Needs to be
		called after a statement has obtained the relevant table locks on
		the 
	*/
	public void checkStatementValidity() throws StandardException;

	/**
	 * Get the result description for this activation, if it has one.
	 *
	 * @return result description for this activation, if it has one;
	 * otherwise, null.
	 */
	ResultDescription getResultDescription();

	/**
	 * Get the DataValueFactory
	 *
	 * @return DataValueFactory
	 */
	DataValueFactory getDataValueFactory();

 
	/**
		Get the saved RowLocation.

		@param itemNumber	The saved item number.

		@return	A RowLocation template for the conglomerate
	 */
	public RowLocation getRowLocationTemplate(int itemNumber);

	/**
		Get the number of subqueries in the entire query.
		@return int	 The number of subqueries in the entire query.
	 */
	public int getNumSubqueries();

	/**
	 * Return the cursor name of this activation. This will differ
	 * from its ResultSet's cursor name if it has been
	 * altered with setCursorName. Thus this always returns the cursor
	 * name of the next execution of this activation. The cursor name
	 * of the current execution must be obtained from the ResultSet.
	 * or this.getResultSet.getCursorName() [with null checking].
	 * <p>
	 * Statements that do not support cursors will return a null.
	 * <p>
	 * @return The cursor name.
	 */
	public String	getCursorName();

	/**
	 * Return the holdability of this activation.
	 * <p>
	 * @return The holdability of this activation.
	 */
	public boolean	getResultSetHoldability();

	/**
	 * Set current resultset holdability.
	 *
	 * @param resultSetHoldability	The new resultset holdability.
	 */
	public void setResultSetHoldability(boolean resultSetHoldability);

	/**
	 * Set the auto-generated keys resultset mode to true for this activation.
	 *
	 * The specific columns for auto-generated keys resultset can be requested by
	 * passing column positions array
	 *
	 * The specific columns for auto-generated keys resultset can be requested by
	 * passing column names array
	 *
	 * Both the parameters would be null if user didn't request specific keys.
	 * Otherwise, the user could request specific columns by passing column positions
	 * or names array but not both.
	 *
	 * @param columnIndexes Request specific columns in auto-generated keys
	 * resultset by passing column positions. null means no specific columns
	 * requested by position
	 *
	 * @param columnNames Request specific columns in auto-generated keys
	 * resultset by passing column names.  null means no specific columns
	 * requested by position
	 */
	public void setAutoGeneratedKeysResultsetInfo(int[] columnIndexes, String[] columnNames);

	/**
	 * Returns true if auto-generated keys resultset request was made for this
	 * avtivation.
	 * <p>
	 * @return auto-generated keys resultset mode for this activation.
	 */
	public boolean	getAutoGeneratedKeysResultsetMode();

	/**
	 * Returns the column positions array of columns requested in auto-generated
	 * keys resultset for this avtivation. Returns null if no specific column
	 * requested by positions
	 * <p>
	 * @return column positions array of columns requested.
	 */
	public int[] getAutoGeneratedKeysColumnIndexes();

	/**
	 * Returns the column names array of columns requested in auto-generated
	 * keys resultset for this avtivation. Returns null if no specific column
	 * requested by names
	 * <p>
	 * @return column names array of columns requested.
	 */
	public String[] getAutoGeneratedKeysColumnNames();

	/**
	 * Mark the activation as unused.  
	 */
	public void markUnused();

	/**
	 * Is the activation in use?
	 *
	 * @return true/false
	 */
	public boolean isInUse();

  
	 
	public Activation getParentActivation();

	/**
	 * Called by generated code to get the next number in an ANSI/ISO sequence
     * and advance the sequence. Raises an exception if the sequence was declared
     * NO CYCLE and its range is exhausted.
	 *
     * @param sequenceUUIDstring The string value of the sequence's UUID
     * @param typeFormatID The format id of the data type to be returned. E.g., StoredFormatIds.SQL_INTEGER_ID.
     *
	 * @return The next number in the sequence
	 */
	public NumberDataValue getCurrentValueAndAdvance
        ( String sequenceUUIDstring, int typeFormatID )
        throws StandardException;
}
