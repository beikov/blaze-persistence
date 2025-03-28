/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */

package com.blazebit.persistence.testsuite.base.jpa;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import javax.persistence.SharedCacheMode;
import javax.persistence.ValidationMode;
import javax.persistence.spi.ClassTransformer;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.persistence.spi.PersistenceUnitTransactionType;
import javax.sql.DataSource;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class MutablePersistenceUnitInfo implements PersistenceUnitInfo {

    private String persistenceUnitName;
    private String persistenceProviderClassName;
    private PersistenceUnitTransactionType transactionType;
    private DataSource nonJtaDataSource;
    private DataSource jtaDataSource;
    private final List<String> mappingFileNames = new LinkedList<String>();
    private List<URL> jarFileUrls = new LinkedList<URL>();
    private URL persistenceUnitRootUrl;
    private final List<String> managedClassNames = new LinkedList<String>();
    private boolean excludeUnlistedClasses = false;
    private SharedCacheMode sharedCacheMode = SharedCacheMode.UNSPECIFIED;
    private ValidationMode validationMode = ValidationMode.AUTO;
    private Properties properties = new Properties();
    private String persistenceXMLSchemaVersion = "2.0";

    public void setPersistenceUnitName(String persistenceUnitName) {
        this.persistenceUnitName = persistenceUnitName;
    }

    @Override
    public String getPersistenceUnitName() {
        return this.persistenceUnitName;
    }

    public void setPersistenceProviderClassName(String persistenceProviderClassName) {
        this.persistenceProviderClassName = persistenceProviderClassName;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return this.persistenceProviderClassName;
    }

    public void setTransactionType(PersistenceUnitTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        if (this.transactionType != null) {
            return this.transactionType;
        } else {
            return (this.jtaDataSource != null
                ? PersistenceUnitTransactionType.JTA : PersistenceUnitTransactionType.RESOURCE_LOCAL);
        }
    }

    public void setJtaDataSource(DataSource jtaDataSource) {
        this.jtaDataSource = jtaDataSource;
    }

    @Override
    public DataSource getJtaDataSource() {
        return this.jtaDataSource;
    }

    public void setNonJtaDataSource(DataSource nonJtaDataSource) {
        this.nonJtaDataSource = nonJtaDataSource;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return this.nonJtaDataSource;
    }

    public void addMappingFileName(String mappingFileName) {
        this.mappingFileNames.add(mappingFileName);
    }

    @Override
    public List<String> getMappingFileNames() {
        return this.mappingFileNames;
    }

    public void addJarFileUrl(URL jarFileUrl) {
        this.jarFileUrls.add(jarFileUrl);
    }

    @Override
    public List<URL> getJarFileUrls() {
        return this.jarFileUrls;
    }

    public void setPersistenceUnitRootUrl(URL persistenceUnitRootUrl) {
        this.persistenceUnitRootUrl = persistenceUnitRootUrl;
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return this.persistenceUnitRootUrl;
    }

    public void addManagedClassName(String managedClassName) {
        this.managedClassNames.add(managedClassName);
    }

    @Override
    public List<String> getManagedClassNames() {
        return this.managedClassNames;
    }

    public void setExcludeUnlistedClasses(boolean excludeUnlistedClasses) {
        this.excludeUnlistedClasses = excludeUnlistedClasses;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return this.excludeUnlistedClasses;
    }

    public void setSharedCacheMode(SharedCacheMode sharedCacheMode) {
        this.sharedCacheMode = sharedCacheMode;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return this.sharedCacheMode;
    }

    public void setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
    }

    @Override
    public ValidationMode getValidationMode() {
        return this.validationMode;
    }

    public void addProperty(String name, String value) {
        if (this.properties == null) {
            this.properties = new Properties();
        }
        this.properties.setProperty(name, value);
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Properties getProperties() {
        return this.properties;
    }

    public void setPersistenceXMLSchemaVersion(String persistenceXMLSchemaVersion) {
        this.persistenceXMLSchemaVersion = persistenceXMLSchemaVersion;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return this.persistenceXMLSchemaVersion;
    }

    /**
     * This implementation returns the default ClassLoader.
     *
     * From <code>org.springframework.util.ClassUtils#getDefaultClassLoader()</code>
     */
    @Override
    public ClassLoader getClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread()
                .getContextClassLoader();
        } catch (Throwable ex) {
            // Cannot access thread context ClassLoader - falling back...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = MutablePersistenceUnitInfo.class.getClassLoader();
            if (cl == null) {
                // getClassLoader() returning null indicates the bootstrap ClassLoader
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable ex) {
                    // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
                }
            }
        }
        return cl;
    }

    @Override
    public void addTransformer(ClassTransformer classTransformer) {
        // Not required for the tests
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return new ClassLoader(getClassLoader()) {
        };
    }

    @Override
    public String toString() {
        return "PersistenceUnitInfo: name '" + this.persistenceUnitName + "', root URL [" + this.persistenceUnitRootUrl + "]";
    }

}
