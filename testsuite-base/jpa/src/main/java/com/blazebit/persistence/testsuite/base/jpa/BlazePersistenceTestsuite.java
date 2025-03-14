/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Blazebit
 */
package com.blazebit.persistence.testsuite.base.jpa;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Moritz Becker
 * @since 1.5.0
 */
public abstract class BlazePersistenceTestsuite {

    protected static Class<?>[] loadExcludedGroups() {
        String excludedGroupsProperty = System.getProperty("excludedGroups");
        Class<?>[] excludedGroups;
        if (excludedGroupsProperty == null) {
            excludedGroups = new Class<?>[0];
        } else {
            excludedGroups = Arrays.stream(excludedGroupsProperty.split(","))
                    .map(groupName -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(groupName.trim());
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }).toArray(Class<?>[]::new);
        }
        return excludedGroups;
    }

    protected static TestClasses loadAndGroupTestClasses() {
        String testsuitePackage = System.getProperty("testBasePackage");
        Map<Set<Class<?>>, List<Class<? extends AbstractJpaPersistenceTest>>> groupedJpaPersistenceTests;
        Set<Class<?>> nonJpaPersistenceTests = new HashSet<>();
        try (ScanResult scanResult =
                     new ClassGraph()
                             .enableAnnotationInfo()
                             .enableMethodInfo()
                             .enableClassInfo()
                             .enableExternalClasses()
                             .acceptPackages(testsuitePackage)
                             .scan()) {

            ClassInfoList allTests = scanResult.getClassesWithMethodAnnotation(Test.class.getName());
            ClassInfoList potentialJpaPersistenceTests = scanResult.getSubclasses(AbstractJpaPersistenceTest.class.getName());

            Set<ClassInfo> jpaPersistenceTests = new HashSet<>();
            for (ClassInfo testClass : allTests) {
                if (potentialJpaPersistenceTests.contains(testClass)) {
                    jpaPersistenceTests.add(testClass);
                } else {
                    try {
                        nonJpaPersistenceTests.add(Thread.currentThread().getContextClassLoader().loadClass(testClass.getName()));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            groupedJpaPersistenceTests = jpaPersistenceTests.stream()
                    .collect(Collectors.groupingBy(
                        jpaPersistenceTestInfo -> {
                            AbstractJpaPersistenceTest jpaPersistenceTest = instantiateAbstractJpaPersistenceTest(jpaPersistenceTestInfo);
                            return getEntityClasses(jpaPersistenceTest);
                        },
                        Collectors.mapping((Function<? super ClassInfo, ? extends Class<? extends AbstractJpaPersistenceTest>>) jpaPersistenceTestInfo -> {
                            try {
                                return (Class<? extends AbstractJpaPersistenceTest>) Thread.currentThread().getContextClassLoader().loadClass(jpaPersistenceTestInfo.getName());
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }, Collectors.toList())
                    ));
        }
        return new TestClasses(groupedJpaPersistenceTests, nonJpaPersistenceTests);
    }

    private static Set<Class<?>> getEntityClasses(AbstractJpaPersistenceTest jpaPersistenceTest) {
        try {
            Method m = AbstractJpaPersistenceTest.class.getDeclaredMethod("getEntityClasses");
            m.setAccessible(true);
            return new HashSet<>(Arrays.asList((Class<?>[]) m.invoke(jpaPersistenceTest)));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static AbstractJpaPersistenceTest instantiateAbstractJpaPersistenceTest(ClassInfo testClassInfo) {
        AbstractJpaPersistenceTest testInstance;
        Class<AbstractJpaPersistenceTest> testClass;
        try {
            testClass = (Class<AbstractJpaPersistenceTest>) Thread.currentThread().getContextClassLoader().loadClass(testClassInfo.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        MethodInfoList parametersMethods = testClassInfo.getMethodInfo()
                .filter(methodInfo -> methodInfo.getAnnotationInfo(Parameterized.Parameters.class.getName()) != null);
        if (parametersMethods.isEmpty()) {
            try {
                testInstance = testClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } else if (parametersMethods.size() == 1) {
            try {
                Method parametersMethod = testClass.getMethod(parametersMethods.get(0).getName());
                Object parameters = parametersMethod.invoke(null);
                Object firstParametersOrSingleParameter;
                if (parameters instanceof Iterable) {
                    firstParametersOrSingleParameter = ((Collection<?>) parameters).iterator().next();
                } else {
                    firstParametersOrSingleParameter = ((Object[]) parameters)[0];
                }
                Constructor<AbstractJpaPersistenceTest>[] constructors = (Constructor<AbstractJpaPersistenceTest>[]) testClass.getConstructors();
                if (constructors.length != 1) {
                    throw new RuntimeException(String.format("Found more than 1 constructor in test class %s declaring an %s annotated method.",
                        testClass.getName(), Parameterized.class.getName()));
                }
                Object[] firstParameters = firstParametersOrSingleParameter instanceof Object[]
                    ? (Object[]) firstParametersOrSingleParameter : new Object[]{firstParametersOrSingleParameter};
                testInstance = constructors[0].newInstance(firstParameters);
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException(
                    "Found more than 1 method annotated with " +
                            Parameterized.Parameters.class.getName() +
                            " in test class " + testClassInfo.getName());
        }
        return testInstance;
    }

    static final class TestClasses {
        final Map<Set<Class<?>>, List<Class<? extends AbstractJpaPersistenceTest>>> groupedJpaPersistenceTests;
        final Set<Class<?>> nonJpaPersistenceTests;

        private TestClasses(Map<Set<Class<?>>, List<Class<? extends AbstractJpaPersistenceTest>>> groupedJpaPersistenceTests, Set<Class<?>> nonJpaPersistenceTests) {
            this.groupedJpaPersistenceTests = groupedJpaPersistenceTests;
            this.nonJpaPersistenceTests = nonJpaPersistenceTests;
        }
    }
}
