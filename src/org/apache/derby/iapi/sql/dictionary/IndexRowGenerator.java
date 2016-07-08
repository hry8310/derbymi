/*

   Derby - Class org.apache.derby.iapi.sql.dictionary.IndexRowGenerator

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

package org.apache.derby.iapi.sql.dictionary;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.apache.derby.catalog.IndexDescriptor;
import org.apache.derby.catalog.types.IndexDescriptorImpl;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.context.ContextService;
import org.apache.derby.iapi.services.io.Formatable;
import org.apache.derby.iapi.services.io.FormatableBitSet;
import org.apache.derby.iapi.services.io.StoredFormatIds;
import org.apache.derby.shared.common.sanity.SanityManager; 
import org.apache.derby.iapi.types.RowLocation;
import org.apache.derby.iapi.types.StringDataValue;
import org.apache.derby.iapi.types.DataTypeDescriptor;

/**
 * This class extends IndexDescriptor for internal use by the
 * DataDictionary.
 * <p>
 * For a description of how deferrable and non-deferrable constraints
 * are backed differently, including the meaning of the
 * boolean attributes used here, see {@link
 * org.apache.derby.catalog.IndexDescriptor}.
 */
public class IndexRowGenerator implements IndexDescriptor, Formatable
{
	private IndexDescriptor	id; 

    /**
     * Constructor for an IndexRowGeneratorImpl
     * 
     * @param indexType		The type of index
     * @param isUnique		True means the index is unique
     * @param isUniqueWithDuplicateNulls means the index is almost unique
     *                              i.e. unique only for non null keys
     * @param isUniqueDeferrable    True means the index represents a PRIMARY
     *                              KEY or a UNIQUE NOT NULL constraint which
     *                              is deferrable.
     * @param hasDeferrableChecking True if the index is used to back a
     *                              deferrable constraint
     * @param baseColumnPositions	An array of column positions in the base
     * 								table.  Each index column corresponds to a
     * 								column position in the base table.
     * @param isAscending	An array of booleans telling asc/desc on each
     * 						column.
     * @param numberOfOrderedColumns	In the future, it will be possible
     * 									to store non-ordered columns in an
     * 									index.  These will be useful for
     * 									covered queries.
     */
	public IndexRowGenerator(String indexType,
								boolean isUnique,
								boolean isUniqueWithDuplicateNulls,
                                boolean isUniqueDeferrable,
                                boolean hasDeferrableChecking,
								int[] baseColumnPositions,
								boolean[] isAscending,
								int numberOfOrderedColumns)
	{
		id = new IndexDescriptorImpl(indexType,
									isUnique,
									isUniqueWithDuplicateNulls,
                                    isUniqueDeferrable,
                                    hasDeferrableChecking,
									baseColumnPositions,
									isAscending,
									numberOfOrderedColumns);

		if (SanityManager.DEBUG)
		{
			SanityManager.ASSERT(baseColumnPositions != null,
				"baseColumnPositions are null");
		}
	}

	/**
	 * Constructor for an IndexRowGeneratorImpl
	 *
	 * @param indexDescriptor		An IndexDescriptor to delegate calls to
	 */
	public IndexRowGenerator(IndexDescriptor indexDescriptor)
	{
		id = indexDescriptor;
	}

	   

    /**
     * Return an array of collation ids for this table.
     * <p>
     * Return an array of collation ids, one for each column in the
     * columnDescriptorList.  This is useful for passing collation id info
     * down to store, for instance in createConglomerate() to create
     * the index.
     *
     * This is only expected to get called during ddl, so object allocation
     * is ok. 
     *
	 * @param columnList ColumnDescriptors describing the base table.
     *
	 * @exception  StandardException  Standard exception policy.
     **/
    public int[] getColumnCollationIds(ColumnDescriptorList columnList)
		throws StandardException
    {
        int[] base_cols     = id.baseColumnPositions();
        int[] collation_ids = new int[base_cols.length + 1];

		for (int i = 0; i < base_cols.length; i++)
		{
            collation_ids[i] =
				columnList.elementAt(
                    base_cols[i] - 1).getType().getCollationType();
		}

        // row location column at end is always basic collation type.
        collation_ids[collation_ids.length - 1] = 
            StringDataValue.COLLATION_TYPE_UCS_BASIC; 

		return(collation_ids);
    }

		 
	/**
	 * Get the IndexDescriptor that this IndexRowGenerator is based on.
	 */
	public IndexDescriptor getIndexDescriptor()
	{
		return id;
	}

	/** Zero-argument constructor for Formatable interface */
	public IndexRowGenerator()
	{
	}

	/**
     * @see IndexDescriptor#isUniqueWithDuplicateNulls
     */
	public boolean isUniqueWithDuplicateNulls()
	{
		return id.isUniqueWithDuplicateNulls();
	}

    public boolean hasDeferrableChecking()
    {
        return id.hasDeferrableChecking();
    }


    public boolean isUniqueDeferrable()
    {
        return id.isUniqueDeferrable();
    }


	/** @see IndexDescriptor#isUnique */
	public boolean isUnique()
	{
		return id.isUnique();
	}

	/** @see IndexDescriptor#baseColumnPositions */
	public int[] baseColumnPositions()
	{
		return id.baseColumnPositions();
	}

	/** @see IndexDescriptor#getKeyColumnPosition */
	public int getKeyColumnPosition(int heapColumnPosition)
	{
		return id.getKeyColumnPosition(heapColumnPosition);
	}

	/** @see IndexDescriptor#numberOfOrderedColumns */
	public int numberOfOrderedColumns()
	{
		return id.numberOfOrderedColumns();
	}

	/** @see IndexDescriptor#indexType */
	public String indexType()
	{
		return id.indexType();
	}

	public String toString()
	{
		return id.toString();
	}

	/** @see IndexDescriptor#isAscending */
	public boolean			isAscending(Integer keyColumnPosition)
	{
		return id.isAscending(keyColumnPosition);
	}

	/** @see IndexDescriptor#isDescending */
	public boolean			isDescending(Integer keyColumnPosition)
	{
		return id.isDescending(keyColumnPosition);
	}

	/** @see IndexDescriptor#isAscending */
	public boolean[]		isAscending()
	{
		return id.isAscending();
	}

	/** @see IndexDescriptor#setBaseColumnPositions */
	public void		setBaseColumnPositions(int[] baseColumnPositions)
	{
		id.setBaseColumnPositions(baseColumnPositions);
	}

	/** @see IndexDescriptor#setIsAscending */
	public void		setIsAscending(boolean[] isAscending)
	{
		id.setIsAscending(isAscending);
	}

	/** @see IndexDescriptor#setNumberOfOrderedColumns */
	public void		setNumberOfOrderedColumns(int numberOfOrderedColumns)
	{
		id.setNumberOfOrderedColumns(numberOfOrderedColumns);
	}

	/**
	 * Test for value equality
	 *
	 * @param other		The other indexrowgenerator to compare this one with
	 *
	 * @return	true if this indexrowgenerator has the same value as other
	 */

	public boolean equals(Object other)
	{
		return id.equals(other);
	}

	/**
	  @see java.lang.Object#hashCode
	  */
	public int hashCode()
	{
		return id.hashCode();
	}

	 

	////////////////////////////////////////////////////////////////////////////
	//
	// EXTERNALIZABLE
	//
	////////////////////////////////////////////////////////////////////////////

	/**
	 * @see java.io.Externalizable#readExternal
	 *
	 * @exception IOException	Thrown on read error
	 * @exception ClassNotFoundException	Thrown on read error
	 */
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
		id = (IndexDescriptor)in.readObject();
	}

	/**
	 *
	 * @exception IOException	Thrown on write error
	 */
	public void writeExternal(ObjectOutput out) throws IOException
	{
		out.writeObject(id);
	}

	/* TypedFormat interface */
	public int getTypeFormatId()
	{
		return StoredFormatIds.INDEX_ROW_GENERATOR_V01_ID;
	}

}
