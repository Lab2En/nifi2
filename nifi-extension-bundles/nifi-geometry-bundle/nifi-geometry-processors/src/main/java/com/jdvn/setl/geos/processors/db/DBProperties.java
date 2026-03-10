package com.jdvn.setl.geos.processors.db;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.util.StandardValidators;

public final class DBProperties {

    private DBProperties() {}

    public static final PropertyDescriptor USE_AVRO_LOGICAL_TYPES =
            new PropertyDescriptor.Builder()
                    .name("use-avro-logical-types")
                    .displayName("Use Avro Logical Types")
                    .description("Whether to use Avro logical types for date/time values.")
                    .allowableValues("true", "false")
                    .defaultValue("false")
                    .required(true)
                    .build();

    public static final PropertyDescriptor VARIABLE_REGISTRY_ONLY_DEFAULT_PRECISION =
            new PropertyDescriptor.Builder()
                    .name("default-precision")
                    .displayName("Default Decimal Precision")
                    .description("Default precision for decimal fields when not provided.")
                    .addValidator(StandardValidators.INTEGER_VALIDATOR)
                    .defaultValue("10")
                    .required(true)
                    .build();

    public static final PropertyDescriptor VARIABLE_REGISTRY_ONLY_DEFAULT_SCALE =
            new PropertyDescriptor.Builder()
                    .name("default-scale")
                    .displayName("Default Decimal Scale")
                    .description("Default scale for decimal fields when not provided.")
                    .addValidator(StandardValidators.INTEGER_VALIDATOR)
                    .defaultValue("0")
                    .required(true)
                    .build();
}