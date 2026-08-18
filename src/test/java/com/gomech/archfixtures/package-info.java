/**
 * Architecture fixtures that must live outside {@code com.gomech.api}.
 *
 * <p>Most violating fixtures sit in {@code com.gomech.api.architecture.fixtures}. A fixture carrying
 * a persistence or Spring stereotype annotation cannot: the application is scanned from
 * {@code com.gomech.api}, so an {@code @Entity} there would be picked up by Hibernate and fail
 * schema validation in {@code GoMechV2ApiApplicationIT}, and a {@code @Component} would be
 * registered as a bean.
 *
 * <p>This package is a sibling of {@code com.gomech.api}, so nothing here is component scanned,
 * entity scanned, or included in the production ArchUnit import. The rules still apply to it,
 * because they match package patterns such as {@code ..modules..application..} which are
 * prefix-independent.
 */
package com.gomech.archfixtures;
