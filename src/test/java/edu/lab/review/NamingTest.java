package edu.lab.review;

import edu.lab.review.config.LabConfig;
import edu.lab.review.model.Dimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingTest {
    @Test
    void resourcesCarryStudentId() {
        LabConfig config = LabConfig.load(null);
        String id = config.getStudentId();
        assertTrue(config.taskTopic().contains(id));
        assertTrue(config.resultTopic().contains(id));
        assertTrue(config.groupOf(Dimension.STYLE).contains(id));
        assertTrue(config.groupOf(Dimension.SUMMARY).contains(id));
        assertTrue(config.supervisorGroup().contains(id));

        String session = config.newSessionId();
        assertTrue(session.startsWith(id + "-review-"));
        String key = config.key(session, Dimension.SECURITY, "REQ");
        assertTrue(key.startsWith(id + "_"));
        assertTrue(key.contains("SECURITY_REQ"));
    }

    @Test
    void supervisorLiteGroupUsesLiteNamingConvention() {
        LabConfig config = LabConfig.load(null);
        assertEquals("GID_" + config.getStudentId() + "_supervisor", config.supervisorGroup());
    }
}