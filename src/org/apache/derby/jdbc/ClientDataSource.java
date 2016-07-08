/*

   Derby - Class org.apache.derby.client.ClientDataSource

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

package org.apache.derby.jdbc;

import java.util.Enumeration;
import java.util.Properties;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import org.apache.derby.client.ClientDataSourceFactory;

/**
 * This data source is suitable for a client/server use of Derby,
 * running on full Java SE 6 and higher, corresponding to JDBC 4.0 and higher.
 * <p/>
 * ClientDataSource is a simple data source implementation
 * that can be used for establishing connections in a
 * non-pooling, non-distributed environment.
 * The class ClientConnectionPoolDataSource can be used in a connection pooling environment,
 * and the class ClientXADataSource can be used in a distributed, and pooling
 * environment.
 *
 * <p>The example below registers a DNC data source object with a JNDI naming service.
 * <pre>
 * org.apache.derby.client.ClientDataSource dataSource = new org.apache.derby.client.ClientDataSource ();
 * dataSource.setServerName ("my_derby_database_server");
 * dataSource.setDatabaseName ("my_derby_database_name");
 * javax.naming.Context context = new javax.naming.InitialContext();
 * context.bind ("jdbc/my_datasource_name", dataSource);
 * </pre>
 * The first line of code in the example creates a data source object.
 * The next two lines initialize the data source's
 * properties. Then a Java object that references the initial JNDI naming
 * context is created by calling the
 * InitialContext() constructor, which is provided by JNDI.
 * System properties (not shown) are used to tell JNDI the
 * service provider to use. The JNDI name space is hierarchical,
 * similar to the directory structure of many file
 * systems. The data source object is bound to a logical JNDI name
 * by calling Context.bind(). In this case the JNDI name
 * identifies a subcontext, "jdbc", of the root naming context
 * and a logical name, "my_datasource_name", within the jdbc
 * subcontext. This is all of the code required to deploy
 * a data source object within JNDI. This example is provided
 * mainly for illustrative purposes. We expect that developers
 * or system administrators will normally use a GUI tool to
 * deploy a data source object.
 * <p/>
 * Once a data source has been registered with JNDI,
 * it can then be used by a JDBC application, as is shown in the
 * following example.
 * <pre>
 * javax.naming.Context context = new javax.naming.InitialContext ();
 * javax.sql.DataSource dataSource = (javax.sql.DataSource) context.lookup ("jdbc/my_datasource_name");
 * java.sql.Connection connection = dataSource.getConnection ("user", "password");
 * </pre>
 * The first line in the example creates a Java object
 * that references the initial JNDI naming context. Next, the
 * initial naming context is used to do a lookup operation
 * using the logical name of the data source. The
 * Context.lookup() method returns a reference to a Java Object,
 * which is narrowed to a javax.sql.DataSource object. In
 * the last line, the DataSource.getConnection() method
 * is called to produce a database connection.
 * <p/>
 * This simple data source subclass of BasicClientDataSource40 maintains
 * it's own private <code>password</code> property.
 * <p/>
 * The specified password, along with the user, is validated by DERBY.
 * This property can be overwritten by specifying
 * the password parameter on the DataSource.getConnection() method call.
 * <p/>
 * This password property is not declared transient, and therefore
 * may be serialized to a file in clear-text, or stored
 * to a JNDI server in clear-text when the data source is saved.
 * Care must taken by the user to prevent security
 * breaches.
 * <p/>
 */
public class ClientDataSource extends BasicClientDataSource40 
                              implements Referenceable {

    private final static long serialVersionUID = 1894299584216955553L;
    public static final String className__ = "org.apache.derby.jdbc.ClientDataSource";

    // If a newer version of a serialized object has to be compatible with an older version, it is important that the newer version abides
    // by the rules for compatible and incompatible changes.
    //
    // A compatible change is one that can be made to a new version of the class, which still keeps the stream compatible with older
    // versions of the class. Examples of compatible changes are:
    //
    // Addition of new fields or classes does not affect serialization, as any new data in the stream is simply ignored by older
    // versions. When the instance of an older version of the class is deserialized, the newly added field will be set to its default
    // value.
    // You can field change access modifiers like private, public, protected or package as they are not reflected to the serial
    // stream.
    // You can change a transient or static field to a non-transient or non-static field, as it is similar to adding a field.
    // You can change the access modifiers for constructors and methods of the class. For instance a previously private method
    // can now be made public, an instance method can be changed to static, etc. The only exception is that you cannot change
    // the default signatures for readObject() and writeObject() if you are implementing custom serialization. The serialization
    // process looks at only instance data, and not the methods of a class.
    //
    // Changes which would render the stream incompatible are:
    //
    // Once a class implements the Serializable interface, you cannot later make it implement the Externalizable interface, since
    // this will result in the creation of an incompatible stream.
    // Deleting fields can cause a problem. Now, when the object is serialized, an earlier version of the class would set the old
    // field to its default value since nothing was available within the stream. Consequently, this default data may lead the newly
    // created object to assume an invalid state.
    // Changing a non-static into static or non-transient into transient is not permitted as it is equivalent to deleting fields.
    // You also cannot change the field types within a class, as this would cause a failure when attempting to read in the original
    // field into the new field.
    // You cannot alter the position of the class in the class hierarchy. Since the fully-qualified class name is written as part of
    // the bytestream, this change will result in the creation of an incompatible stream.
    // You cannot change the name of the class or the package it belongs to, as that information is written to the stream during
    // serialization.


    /**
     * Creates a simple DERBY data source with default property values for a non-pooling, non-distributed environment.
     * No particular DatabaseName or other properties are associated with the data source.
     * <p/>
     * Every Java Bean should provide a constructor with no arguments since many beanboxes attempt to instantiate a bean
     * by invoking its no-argument constructor.
     */
    public ClientDataSource() {
        super();
    }

    //------------------------ Referenceable interface methods -----------------------------

    @Override
    public Reference getReference() throws NamingException {

        // This method creates a new Reference object to represent this data
        // source.  The class name of the data source object is saved in the
        // Reference, so that an object factory will know that it should
        // create an instance of that class when a lookup operation is
        // performed. The class name of the object factory,
        // org.apache.derby.client.ClientBaseDataSourceFactory, is also stored
        // in the reference.  This is not required by JNDI, but is recommend
        // in practice.  JNDI will always use the object factory class
        // specified in the reference when reconstructing an object, if a
        // class name has been specified.
        //
        // See the JNDI SPI documentation for further details on this topic,
        // and for a complete description of the Reference and StringRefAddr
        // classes.
        //
        // This BasicClientDataSource40 class provides several standard JDBC
        // properties.  The names and values of the data source properties are
        // also stored in the reference using the StringRefAddr class.  This
        // is all the information needed to reconstruct a ClientDataSource
        // object.

        Reference ref = new Reference(this.getClass().getName(),
                ClientDataSourceFactory.class.getName(), null);

        addBeanProperties(ref);
        return ref;
    }

    /**
     * Add Java Bean properties to the reference using
     * StringRefAddr for each property. List of bean properties
     * is defined from the public getXXX() methods on this object
     * that take no arguments and return short, int, boolean or String.
     * The StringRefAddr has a key of the Java bean property name,
     * converted from the method name. E.g. traceDirectory for
     * traceDirectory.
     *
     * @param ref The referenced object
      */
    private void addBeanProperties(Reference ref) {

        Properties p = getProperties(this);
        Enumeration<?> e = p.propertyNames();

        while (e.hasMoreElements()) {
            String propName = (String)e.nextElement();
            Object value = p.getProperty(propName);
            if (value != null) {
                ref.add(new StringRefAddr(propName, value.toString()));
            }
        }
    }
}

