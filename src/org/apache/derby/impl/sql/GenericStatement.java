/*

   Derby - Class org.apache.derby.impl.sql.GenericStatement

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

package org.apache.derby.impl.sql;

import java.sql.Timestamp;

import org.apache.dearbaby.impl.sql.compile.StatementNode;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState; 
import org.apache.derby.iapi.services.stream.HeaderPrintWriter;
import org.apache.derby.iapi.sql.PreparedStatement;
import org.apache.derby.iapi.sql.Statement;
import org.apache.derby.iapi.sql.compile.ASTVisitor;
import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.compile.Parser;
import org.apache.derby.iapi.sql.compile.Visitable;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.StatementContext;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor;
import org.apache.derby.iapi.util.InterruptStatus; 
import org.apache.derby.shared.common.sanity.SanityManager;

public class GenericStatement implements Statement {

	// these fields define the identity of the statement
	private final SchemaDescriptor compilationSchema;
	private final String statementText;
	private final boolean isForReadOnly;
	private int prepareIsolationLevel;
	private GenericPreparedStatement preparedStmt;

	/**
	 * Constructor for a Statement given the text of the statement in a String
	 * 
	 * @param compilationSchema
	 *            schema
	 * @param statementText
	 *            The text of the statement
	 * @param isForReadOnly
	 *            if the statement is opened with level CONCUR_READ_ONLY
	 */

	public GenericStatement(SchemaDescriptor compilationSchema,
			String statementText, boolean isForReadOnly) {
		this.compilationSchema = compilationSchema;
		this.statementText = statementText;
		this.isForReadOnly = isForReadOnly;
	}

	/*
	 * Statement interface
	 */

	/* RESOLVE: may need error checking, debugging code here */
	public PreparedStatement prepare(LanguageConnectionContext lcc)
			throws StandardException {
		/*
		 * * Note: don't reset state since this might be* a recompilation of an
		 * already prepared statement.
		 */
		return prepare(lcc, false);
	}

	public PreparedStatement prepare(LanguageConnectionContext lcc,
			boolean forMetaData) throws StandardException {
		/*
		 * * Note: don't reset state since this might be* a recompilation of an
		 * already prepared statement.
		 */

		final int depth = lcc.getStatementDepth();
		String prevErrorId = null;
		while (true) {
			boolean recompile = false;
			try {
				return prepMinion(lcc, true, (Object[]) null,
						(SchemaDescriptor) null, forMetaData);
			} catch (StandardException se) {
				// There is a chance that we didn't see the invalidation
				// request from a DDL operation in another thread because
				// the statement wasn't registered as a dependent until
				// after the invalidation had been completed. Assume that's
				// what has happened if we see a conglomerate does not exist
				// error, and force a retry even if the statement hasn't been
				// invalidated.
				if (SQLState.STORE_CONGLOMERATE_DOES_NOT_EXIST.equals(se
						.getMessageId())) {
					// STORE_CONGLOMERATE_DOES_NOT_EXIST has exactly one
					// argument: the conglomerate id
					String conglomId = String.valueOf(se.getArguments()[0]);

					// Request a recompile of the statement if a conglomerate
					// disappears while we are compiling it. But if we have
					// already retried once because the same conglomerate was
					// missing, there's probably no hope that yet another retry
					// will help, so let's break out instead of potentially
					// looping infinitely.
					if (!conglomId.equals(prevErrorId)) {
						recompile = true;
					}

					prevErrorId = conglomId;
				}
				throw se;
			} finally {
				// Check if the statement was invalidated while it was
				// compiled. If so, the newly compiled plan may not be
				// up to date anymore, so we recompile the statement
				// if this happens. Note that this is checked in a finally
				// block, so we also retry if an exception was thrown. The
				// exception was probably thrown because of the changes
				// that invalidated the statement. If not, recompiling
				// will also fail, and the exception will be exposed to
				// the caller.
				//
				// invalidatedWhileCompiling and isValid are protected by
				// synchronization on the prepared statement.
				synchronized (preparedStmt) {
					if (recompile || preparedStmt.invalidatedWhileCompiling) {
						preparedStmt.isValid = false;
						preparedStmt.invalidatedWhileCompiling = false;
						recompile = true;
					}
				}

				if (recompile) {
					// A new statement context is pushed while compiling.
					// Typically, this context is popped by an error
					// handler at a higher level. But since we retry the
					// compilation, the error handler won't be invoked, so
					// the stack must be reset to its original state first.
					while (lcc.getStatementDepth() > depth) {
						lcc.popStatementContext(lcc.getStatementContext(), null);
					}

					// Don't return yet. The statement was invalidated, so
					// we must retry the compilation.
					continue;
				}
			}
		}
	}

	private PreparedStatement prepMinion(LanguageConnectionContext lcc,
			boolean cacheMe, Object[] paramDefaults,
			SchemaDescriptor spsSchema, boolean internalSQL)
			throws StandardException {
		return null;
	}

	/** Walk the AST, using a (user-supplied) Visitor */
	private void walkAST(LanguageConnectionContext lcc, Visitable queryTree,
			int phase) throws StandardException {
		ASTVisitor visitor = lcc.getASTVisitor();
		if (visitor != null) {
			visitor.begin(statementText, phase);
			queryTree.accept(visitor);
			visitor.end(phase);
		}
	}

	/**
	 * Generates an execution plan given a set of named parameters. Does so for
	 * a storable prepared statement.
	 *
	 * @param paramDefaults
	 *            Parameter defaults
	 *
	 * @return A PreparedStatement that allows execution of the execution plan.
	 * @exception StandardException
	 *                Thrown if this is an execution-only version of the module
	 *                (the prepare() method relies on compilation).
	 */
	public PreparedStatement prepareStorable(LanguageConnectionContext lcc,
			PreparedStatement ps, Object[] paramDefaults,
			SchemaDescriptor spsSchema, boolean internalSQL)
			throws StandardException {
		return null;
	}

	public String getSource() {
		return statementText;
	}

	public String getCompilationSchema() {
		return compilationSchema.getDescriptorName();
	}

	private static long getCurrentTimeMillis(LanguageConnectionContext lcc) {
		if (lcc.getStatisticsTiming()) {
			return System.currentTimeMillis();
		} else {
			return 0;
		}
	}

	/**
	 * Return the {@link PreparedStatement} currently associated with this
	 * statement.
	 *
	 * @return the prepared statement that is associated with this statement
	 */
	public PreparedStatement getPreparedStatement() {
		return null;
	}

	/*
	 * * Identity
	 */
	@Override
	public boolean equals(Object other) {

		if (other instanceof GenericStatement) {

			GenericStatement os = (GenericStatement) other;

			return statementText.equals(os.statementText)
					&& isForReadOnly == os.isForReadOnly
					&& compilationSchema.equals(os.compilationSchema)
					&& (prepareIsolationLevel == os.prepareIsolationLevel);
		}

		return false;
	}

	@Override
	public int hashCode() {

		return statementText.hashCode();
	}
}
