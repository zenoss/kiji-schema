/**
 * (c) Copyright 2013 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema.layout.impl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.schema.Kiji;
import org.kiji.schema.KijiClientTest;
import org.kiji.schema.KijiColumnName;
import org.kiji.schema.avro.AvroValidationPolicy;
import org.kiji.schema.avro.EmptyRecord;
import org.kiji.schema.avro.TableLayoutDesc;
import org.kiji.schema.avro.TestRecord1;
import org.kiji.schema.avro.TestRecord4;
import org.kiji.schema.avro.TestRecord5;
import org.kiji.schema.impl.Versions;
import org.kiji.schema.layout.InvalidLayoutSchemaException;
import org.kiji.schema.layout.KijiTableLayout;
import org.kiji.schema.layout.KijiTableLayouts;
import org.kiji.schema.layout.TableLayoutBuilder;

/** Tests for TableLayoutUpdateValidator. */
public class TestTableLayoutUpdateValidator extends KijiClientTest {
  private static final Logger LOG = LoggerFactory.getLogger(TestTableLayoutUpdateValidator.class);

  /**
   * Layout for table 'invalid_avro_validation_test' to test Avro validation in pre-layout-1.3 mode.
   * Layout is internally incompatible, but uses layout-1.2.0 and so is not validated.
   */
  public static final String INVALID_AVRO_VALIDATION_TEST =
      "org/kiji/schema/layout/invalid-avro-validation-test.json";

  /**
   * layout-1.3.0 based Layout for table 'avro_validation_test' which includes a valid set of reader
   * and writer schemas.
   */
  public static final String AVRO_VALIDATION_TEST =
      "org/kiji/schema/layout/avro-validation-test.json";

  /**
   * layout-1.3.0 based Layout for table 'avrovalidationtest' which includes added, modified, and
   * removed columns from {@link #AVRO_VALIDATION_TEST}.
   *
   * <pre>
   *   Updates:
   *     info:qual0 modified to include a new reader with an additional optional field
   *     info:qual1 removed
   *     info:qual2 added
   * </pre>
   */
  public static final String AVRO_VALIDATION_UPDATE_TEST =
      "org/kiji/schema/layout/avro-validation-update-test.json";


  /** Schema containing a single String field. */
  private static final Schema STRING_SCHEMA = Schema.create(Type.STRING);
  /**
   * Schema containing a single Int field. This will not be readable by the registered String
   * schema from the previous layout because Int and String are incompatible types.
   */
  private static final Schema INT_SCHEMA = Schema.create(Type.INT);
  /** Schema containing no fields. */
  private static final Schema EMPTY_SCHEMA = EmptyRecord.SCHEMA$;
  /**
   * Schema containing an optional Int field. This will be readable by the registered Empty schema
   * from the previous layout because all fields are optional.  The old reader schema will read new
   * records written with this schema as empty.
   */
  private static final Schema OPTIONAL_INT_SCHEMA = TestRecord1.SCHEMA$;

  /** Set the system version for this Kiji instance to 2.0. */
  private void setSystemVersion2() throws IOException {
    getKiji().getSystemTable().setDataVersion(Versions.MIN_SYS_VER_FOR_LAYOUT_VALIDATION);
  }

  /**
   * Creates a table with a string reader schema and the given validation policy
   * then builds a new TableLayoutDesc with an int writer schema.
   *
   * @param kiji the Kiji instance in which to create the table.
   * @param policy the AvroValidationPolicy to enforce on the test column.
   * @return a new TableLayoutDesc with with its reference layout set to the old string-reader
   *     layout and a new int-writer schema.
   * @throws IOException
   */
  private TableLayoutDesc prepareNewDesc(Kiji kiji, AvroValidationPolicy policy)
      throws IOException {

    final TableLayoutDesc desc = KijiTableLayouts.getLayout(KijiTableLayouts.SCHEMA_REG_TEST);
    desc.setVersion("layout-1.3.0");

    final TableLayoutDesc originalDesc = new TableLayoutBuilder(desc, kiji)
        .withAvroValidationPolicy(
            new KijiColumnName("info:fullname"), policy)
        .withReader(new KijiColumnName("info:fullname"), STRING_SCHEMA)
        .build();

    kiji.createTable(originalDesc);

    final TableLayoutDesc newDesc = new TableLayoutBuilder(originalDesc, kiji)
        .withWriter(new KijiColumnName("info:fullname"), INT_SCHEMA).build();
    newDesc.setReferenceLayout("1");
    newDesc.setLayoutId("2");

    return newDesc;
  }

  @Test
  public void testAvroValidationChanges() throws IOException {
    final TableLayoutUpdateValidator validator = new TableLayoutUpdateValidator(getKiji());
    final TableLayoutDesc basicDesc = KijiTableLayouts.getLayout(KijiTableLayouts.SCHEMA_REG_TEST);
    basicDesc.setVersion("layout-1.3.0");
    final KijiColumnName validatedColumn = new KijiColumnName("info:fullname");
    final Schema intSchema = Schema.create(Type.INT);
    final Schema stringSchema = Schema.create(Type.STRING);

    final TableLayoutDesc strictIntDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.STRICT)
        .withReader(validatedColumn, intSchema)
        .build();
    final TableLayoutDesc strictStringDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.STRICT)
        .withWriter(validatedColumn, stringSchema)
        .build();
    final TableLayoutDesc developerIntDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.DEVELOPER)
        .withReader(validatedColumn, intSchema)
        .build();
    final TableLayoutDesc developerStringDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.DEVELOPER)
        .withWriter(validatedColumn, stringSchema)
        .build();
    final TableLayoutDesc noneIntDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.NONE)
        .withReader(validatedColumn, intSchema)
        .build();
    final TableLayoutDesc noneStringDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.NONE)
        .withWriter(validatedColumn, stringSchema)
        .build();
    final TableLayoutDesc schema10IntDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.SCHEMA_1_0)
        .withReader(validatedColumn, intSchema)
        .build();
    final TableLayoutDesc schema10StringDesc = new TableLayoutBuilder(basicDesc, getKiji())
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.SCHEMA_1_0)
        .withWriter(validatedColumn, stringSchema)
        .build();

    final List<TableLayoutDesc> intDescs =
        Lists.newArrayList(strictIntDesc, developerIntDesc, noneIntDesc, schema10IntDesc);

    // Increasing or decreasing validation strictness is acceptable in either direction.  The new
    // layout sets the validation policy which will be run.  For an increase in validation to
    // succeed old readers and writers must be internally compatible and compatible with new readers
    // and writers.

    // Test each validation mode modified to STRICT.
    for (TableLayoutDesc intDesc : intDescs) {
      // Increasing the layout validation to STRICT should throw an exception.
      try {
        validator.validate(
            KijiTableLayout.newLayout(intDesc), KijiTableLayout.newLayout(strictStringDesc));
        fail("validate should have thrown InvalidLayoutSchemaException because int and string are "
            + "incompatible.");
      } catch (InvalidLayoutSchemaException ilse) {
        assertTrue(ilse.getReasons().contains(
            "In column: 'info:fullname' Reader schema: \"int\" is incompatible with "
            + "writer schema: \"string\"."));
        assertTrue(ilse.getReasons().size() == 1);
      }
    }

    // Test each validation mode modified to DEVELOPER.
    for (TableLayoutDesc intDesc : intDescs) {
      // Increasing the layout validation to DEVELOPER should throw an exception.
      try {
        validator.validate(
            KijiTableLayout.newLayout(intDesc), KijiTableLayout.newLayout(developerStringDesc));
        fail("validate should have thrown InvalidLayoutSchemaException because int and string are "
            + "incompatible.");
      } catch (InvalidLayoutSchemaException ilse) {
        assertTrue(ilse.getReasons().contains(
            "In column: 'info:fullname' Reader schema: \"int\" is incompatible with "
            + "writer schema: \"string\"."));
        assertTrue(ilse.getReasons().size() == 1);
      }
    }

    // Test each validation mode modified to SCHEMA_1_0.
    for (TableLayoutDesc intDesc : intDescs) {
      // Reducing the layout validation to SCHEMA_1_0 should eliminate errors.
      validator.validate(
          KijiTableLayout.newLayout(intDesc), KijiTableLayout.newLayout(schema10StringDesc));
    }

    // Test each validation mode modified to NONE.
    for (TableLayoutDesc intDesc : intDescs) {
      // Reducing the layout validation to NONE should eliminate errors.
      validator.validate(
            KijiTableLayout.newLayout(intDesc), KijiTableLayout.newLayout(noneStringDesc));
    }
  }

  @Test
  public void testOldLayoutVersions() throws IOException {
    final TableLayoutUpdateValidator validator = new TableLayoutUpdateValidator(getKiji());
    final TableLayoutDesc desc =
        KijiTableLayouts.getLayout(INVALID_AVRO_VALIDATION_TEST);

    // Layout-1.2.0 (layout specified in the JSON descriptor) does not support validation and so
    // will pass even though the layout is internally incompatible.
    validator.validate(null, KijiTableLayout.newLayout(desc));

    // Layout-1.3.0 does support validation.  The layout is invalid and will throw an exception.
    desc.setVersion("layout-1.3.0");
    try {
      validator.validate(null, KijiTableLayout.newLayout(desc));
      fail("should have thrown InvalidLayoutSchemaException because int and string are "
          + "incompatible.");
    } catch (InvalidLayoutSchemaException ilse) {
      assertTrue(ilse.getReasons().contains(
          "In column: 'info:fullname' Reader schema: \"int\" is incompatible with "
          + "writer schema: \"string\"."));
    }
  }

  @Test
  public void testAddRemoveModifyColumns() throws IOException {
    final TableLayoutUpdateValidator validator = new TableLayoutUpdateValidator(getKiji());
    final KijiColumnName validatedColumn = new KijiColumnName("info:qual0");
    final TableLayoutDesc desc = new TableLayoutBuilder(
        KijiTableLayouts.getLayout(AVRO_VALIDATION_TEST), getKiji())
        .withReader(validatedColumn, TestRecord5.SCHEMA$)
        .withAvroValidationPolicy(validatedColumn, AvroValidationPolicy.STRICT)
        .withLayoutId("original")
        .build();

    // The initial layout is valid.
    validator.validate(null, KijiTableLayout.newLayout(desc));

    // Create an update which removes a column, adds a column, and modifies the set of readers for a
    // column in a compatible way.
    final TableLayoutDesc updateDesc =
        KijiTableLayouts.getLayout(AVRO_VALIDATION_UPDATE_TEST);
    updateDesc.setReferenceLayout("original");
    updateDesc.setLayoutId("updated");
    final TableLayoutDesc newDesc = new TableLayoutBuilder(updateDesc, getKiji())
        .withReader(validatedColumn, TestRecord4.SCHEMA$)
        .build();

    validator.validate(KijiTableLayout.newLayout(desc), KijiTableLayout.newLayout(newDesc));
  }

  @Test
  public void testValidLayoutUpdate() throws IOException {
    setSystemVersion2();
    final Kiji kiji = Kiji.Factory.open(getKiji().getURI());

    final TableLayoutDesc desc = KijiTableLayouts.getLayout(KijiTableLayouts.SCHEMA_REG_TEST);
    desc.setVersion("layout-1.3.0");


    final TableLayoutDesc originalDesc = new TableLayoutBuilder(desc, kiji)
        .withAvroValidationPolicy(
            new KijiColumnName("info:fullname"), AvroValidationPolicy.STRICT)
        .withReader(new KijiColumnName("info:fullname"), EMPTY_SCHEMA)
        .build();

    kiji.createTable(originalDesc);

    final TableLayoutDesc newDesc = new TableLayoutBuilder(originalDesc, kiji)
        .withWriter(new KijiColumnName("info:fullname"), OPTIONAL_INT_SCHEMA)
        .build();
    newDesc.setReferenceLayout("1");
    newDesc.setLayoutId("2");

    kiji.modifyTableLayout(newDesc);
  }

  @Test
  public void testStrictValidation() throws IOException {
    setSystemVersion2();
    final Kiji kiji = Kiji.Factory.open(getKiji().getURI());

    // Strict validation should fail.
    final TableLayoutDesc strictDesc = prepareNewDesc(kiji, AvroValidationPolicy.STRICT);
    try {
      kiji.modifyTableLayout(strictDesc);
      fail("Should have thrown an InvalidLayoutSchemaException because int and string are "
          + "incompatible.");
    } catch (InvalidLayoutSchemaException ilse) {
      assertTrue(ilse.getReasons().contains(
          "In column: 'info:fullname' Reader schema: \"string\" is incompatible with "
              + "writer schema: \"int\"."));
      assertTrue(ilse.getReasons().size() == 1);
    }
  }

  @Test
  public void testDeveloperValidation() throws IOException {
    setSystemVersion2();
    final Kiji kiji = Kiji.Factory.open(getKiji().getURI());

    // Developer validation should fail.
    final TableLayoutDesc developerDesc = prepareNewDesc(kiji, AvroValidationPolicy.DEVELOPER);
    try {
      kiji.modifyTableLayout(developerDesc);
      fail("Should have thrown an InvalidLayoutSchemaException because int and string are "
          + "incompatible.");
    } catch (InvalidLayoutSchemaException ilse) {
      assertTrue(ilse.getReasons().contains(
          "In column: 'info:fullname' Reader schema: \"string\" is incompatible with "
              + "writer schema: \"int\"."));
      assertTrue(ilse.getReasons().size() == 1);
    }
  }

  @Test
  public void testNoneValidation() throws IOException {
    setSystemVersion2();
    final Kiji kiji = Kiji.Factory.open(getKiji().getURI());

    // None validation should pass despite the incompatible change.
    final TableLayoutDesc noneDesc = prepareNewDesc(kiji, AvroValidationPolicy.NONE);
    kiji.modifyTableLayout(noneDesc);
  }

  @Test
  public void testSchema10Validation() throws IOException {
    setSystemVersion2();
    final Kiji kiji = Kiji.Factory.open(getKiji().getURI());

    // Schema-1.0 validation should pass despite incompatible changes.
    final TableLayoutDesc schema10Desc = prepareNewDesc(kiji, AvroValidationPolicy.SCHEMA_1_0);
    kiji.modifyTableLayout(schema10Desc);
  }
}
