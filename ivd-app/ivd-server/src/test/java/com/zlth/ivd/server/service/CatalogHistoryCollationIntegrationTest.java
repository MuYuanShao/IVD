package com.zlth.ivd.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.*;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP seam on a task-owned restored database; cannot target the business database. */
@EnabledIfEnvironmentVariable(named="IVD_CATALOG_QA_HTTP",matches="http://127\\.0\\.0\\.1:18088")
class CatalogHistoryCollationIntegrationTest {
    @Test void legacyAndNewCollationsDoNotBreakCatalogOrMergedHistory() throws Exception {
        String jdbc=System.getenv("IVD_CATALOG_QA_JDBC");
        assertNotNull(jdbc);
        assertTrue(jdbc.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/ivd_deployqa_[a-f0-9]{8}.*"));
        try(var connection=DriverManager.getConnection(jdbc,System.getenv("IVD_CATALOG_QA_USER"),System.getenv("IVD_CATALOG_QA_PASSWORD"));
            var statement=connection.createStatement();
            var rows=statement.executeQuery("SELECT COUNT(DISTINCT COLLATION_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('catalog_change_log','catalog_approval_log') AND COLUMN_NAME='change_type'")) {
            assertTrue(rows.next());assertEquals(2,rows.getInt(1),"Fixture must retain differing legacy and new collations");
        }
        var client=HttpClient.newHttpClient();var json=new ObjectMapper();
        String base=System.getenv("IVD_CATALOG_QA_HTTP")+"/api/v1";
        var login=client.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\""+System.getenv("IVD_CATALOG_QA_LOGIN_PASSWORD")+"\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode());var loginBody=json.readTree(login.body());assertEquals(200,loginBody.get("code").asInt());
        String token=loginBody.get("data").get("accessToken").asText();
        for(String path:new String[]{"/license/change-logs?module=CATALOG&page=1&pageSize=20","/license/change-logs?page=3&pageSize=20"}) {
            var response=client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),path);assertEquals(200,json.readTree(response.body()).get("code").asInt(),path);
        }
    }
}
