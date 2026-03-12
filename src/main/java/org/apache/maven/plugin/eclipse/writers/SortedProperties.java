package org.apache.maven.plugin.eclipse.writers;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A {@link Properties} subclass that writes its entries in sorted (alphabetical) key order.
 * <p>
 * The standard {@link Properties#store} method iterates over keys in hash-table order, which
 * is non-deterministic and differs between JVM versions. Using this class ensures that generated
 * {@code .prefs} files have a stable, predictable layout regardless of the JVM used.
 * </p>
 * <p>
 * Both {@link #keys()} (used by Java 8's {@code store}) and {@link #entrySet()} (used by Java 11+
 * {@code store}) are overridden to enforce alphabetical ordering.
 * </p>
 */
public class SortedProperties
    extends Properties
{

    private static final long serialVersionUID = 1L;

    /** Overridden for Java 8 compatibility: {@code Properties.store} used {@code keys()} there. */
    @Override
    public synchronized Enumeration<Object> keys()
    {
        return Collections.enumeration( new TreeSet<>( super.keySet() ) );
    }

    /**
     * Overridden for Java 11+: {@code Properties.store} iterates via {@code entrySet()} in those
     * versions and would bypass the {@link #keys()} override.
     */
    @Override
    public Set<Map.Entry<Object, Object>> entrySet()
    {
        TreeMap<Object, Object> sorted = new TreeMap<>();
        for ( Map.Entry<Object, Object> e : super.entrySet() )
        {
            sorted.put( e.getKey(), e.getValue() );
        }
        return sorted.entrySet();
    }
}
