/*

   Derby - Class org.apache.derby.impl.sql.compile.PrivilegeNode

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
import java.util.List;
import org.apache.derby.catalog.AliasInfo;
import org.apache.derby.catalog.TypeDescriptor;
import org.apache.derby.catalog.types.RoutineAliasInfo;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.reference.SQLState;
import org.apache.derby.iapi.services.context.ContextManager;
import org.apache.derby.iapi.sql.compile.Visitor;
import org.apache.derby.shared.common.sanity.SanityManager; 
import org.apache.derby.iapi.sql.dictionary.AliasDescriptor;
import org.apache.derby.iapi.sql.dictionary.PrivilegedSQLObject;
import org.apache.derby.iapi.sql.dictionary.SchemaDescriptor;
import org.apache.derby.iapi.sql.dictionary.TableDescriptor; 
/**
 * This node represents a set of privileges that are granted or revoked on one object.
 */
class PrivilegeNode extends QueryTreeNode
{
    // Privilege object type
    public static final int TABLE_PRIVILEGES = 0;
    public static final int ROUTINE_PRIVILEGES = 1;
    public static final int SEQUENCE_PRIVILEGES = 2;
    public static final int UDT_PRIVILEGES = 3;
    public static final int AGGREGATE_PRIVILEGES = 4;

    //
    // State initialized when the node is instantiated
    //
    private int objectType;
    private TableName objectName;
    private TablePrivilegesNode specificPrivileges; // Null for routine and usage privs
    private RoutineDesignator routineDesignator; // null for table and usage privs

    private String privilege;  // E.g., PermDescriptor.USAGE_PRIV
    private boolean restrict;
 
    
    /**
     * Initialize a PrivilegeNode for use against SYS.SYSTABLEPERMS and SYS.SYSROUTINEPERMS.
     *
     * @param objectType
     * @param objectOfPrivilege  (a TableName or RoutineDesignator)
     * @param specificPrivileges null for routines and usage
     * @param cm                 the context manager
     */
    PrivilegeNode(int                 objectType,
                  Object              objectOfPrivilege,
                  TablePrivilegesNode specificPrivileges,
                  ContextManager      cm) throws StandardException {
        super(cm);
        this.objectType = objectType;

        if ( SanityManager.DEBUG)
        {
            SanityManager.ASSERT( objectOfPrivilege != null,
                                  "null privilge object");
        }

        switch( this.objectType)
        {
        case TABLE_PRIVILEGES:
            if( SanityManager.DEBUG)
            {
                SanityManager.ASSERT( specificPrivileges != null,
                                      "null specific privileges used with table privilege");
            }
            objectName = (TableName) objectOfPrivilege;
            this.specificPrivileges = specificPrivileges;
            break;
            
        case ROUTINE_PRIVILEGES:
            if( SanityManager.DEBUG)
            {
                SanityManager.ASSERT( specificPrivileges == null,
                                      "non-null specific privileges used with execute privilege");
            }
            routineDesignator = (RoutineDesignator) objectOfPrivilege;
            objectName = routineDesignator.name;
            break;
            
        default:
            throw unimplementedFeature();
        }
    }

    /**
     * Constructor a PrivilegeNode for use against SYS.SYSPERMS.
     *
     * @param objectType E.g., SEQUENCE
     * @param objectName A possibles schema-qualified name
     * @param privilege  A PermDescriptor privilege, e.g.
     *                   {@code PermDescriptor.USAGE_PRIV}
     * @param restrict   True if this is a REVOKE...RESTRICT action
     * @param cm         The context manager
     */
    PrivilegeNode(int            objectType,
                  TableName      objectName,
                  String         privilege,
                  boolean        restrict,
                  ContextManager cm)
    {
        super(cm);
        this.objectType = objectType;
        this.objectName = objectName;
        this.privilege = privilege;
        this.restrict = restrict;
    }
    
     

 

    /** Report an unimplemented feature */
    private StandardException unimplementedFeature()
    {
        return StandardException.newException( SQLState.BTREE_UNIMPLEMENTED_FEATURE );
    }

    @Override
    void acceptChildren(Visitor v) throws StandardException {
        super.acceptChildren(v);

        if (objectName != null) {
            objectName = (TableName) objectName.accept(v);
        }
    }
}
