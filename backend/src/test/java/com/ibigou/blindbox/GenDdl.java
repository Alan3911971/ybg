package com.ibigou.blindbox;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 离线生成物业模块建表 SQL（不连接数据库，按 MySQL 方言输出）。
 * 用法：mvn test-compile 后运行 GenDdl
 */
public class GenDdl {
    public static void main(String[] args) throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            Class<?>[] entities = {
                com.ibigou.blindbox.entity.MerchantPropertyBinding.class,
                com.ibigou.blindbox.entity.PropertyAccessCredential.class,
                com.ibigou.blindbox.entity.PropertyAccessLog.class,
                com.ibigou.blindbox.entity.PropertyActivityAudit.class,
                com.ibigou.blindbox.entity.PropertyAdmin.class,
                com.ibigou.blindbox.entity.PropertyAdminSession.class,
                com.ibigou.blindbox.entity.PropertyAlertRule.class,
                com.ibigou.blindbox.entity.PropertyAutoPayBinding.class,
                com.ibigou.blindbox.entity.PropertyBill.class,
                com.ibigou.blindbox.entity.PropertyBindRelation.class,
                com.ibigou.blindbox.entity.PropertyBuilding.class,
                com.ibigou.blindbox.entity.PropertyCommunity.class,
                com.ibigou.blindbox.entity.PropertyCompany.class,
                com.ibigou.blindbox.entity.PropertyDeductLog.class,
                com.ibigou.blindbox.entity.PropertyDevice.class,
                com.ibigou.blindbox.entity.PropertyDeviceAlert.class,
                com.ibigou.blindbox.entity.PropertyFaceInfo.class,
                com.ibigou.blindbox.entity.PropertyFacility.class,
                com.ibigou.blindbox.entity.PropertyFamilyMember.class,
                com.ibigou.blindbox.entity.PropertyInspectionPlan.class,
                com.ibigou.blindbox.entity.PropertyInspectionRecord.class,
                com.ibigou.blindbox.entity.PropertyOwner.class,
                com.ibigou.blindbox.entity.PropertyParkingFee.class,
                com.ibigou.blindbox.entity.PropertyReservation.class,
                com.ibigou.blindbox.entity.PropertyRoom.class,
                com.ibigou.blindbox.entity.PropertySplitRecord.class,
                com.ibigou.blindbox.entity.PropertyTempParkingPayment.class,
                com.ibigou.blindbox.entity.PropertyValueService.class,
                com.ibigou.blindbox.entity.PropertyVehicle.class,
                com.ibigou.blindbox.entity.PropertyVehicleLog.class,
                com.ibigou.blindbox.entity.PropertyVisitorVehicle.class,
                com.ibigou.blindbox.entity.PropertyVsOrder.class,
                com.ibigou.blindbox.entity.PropertyWoLog.class,
                com.ibigou.blindbox.entity.PropertyWorkorder.class,
            };
            for (Class<?> c : entities) {
                sources.addAnnotatedClass(c);
            }
            Metadata metadata = sources.getMetadataBuilder()
                    .applyPhysicalNamingStrategy(new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy())
                    .build();

            SchemaCreatorImpl creator = new SchemaCreatorImpl(registry);
            List<String> sql = creator.generateCreationCommands(metadata, false);

            String out = System.getProperty("out", "property-schema.sql");
            try (PrintWriter pw = new PrintWriter(new FileWriter(out, StandardCharsets.UTF_8))) {
                for (String line : sql) {
                    pw.println(line + ";");
                }
            }
            System.out.println("DDL written to " + out + ", stmts=" + sql.size() + ", entities=" + metadata.getEntityBindings().size());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
