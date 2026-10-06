package customer.incident_management.handler;

import cds.gen.processorservice.Incidents;
import cds.gen.processorservice.ProcessorService_;
import cds.gen.sap.capire.incidents.*;
import com.sap.cds.ql.Select;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ServiceName(ProcessorService_.CDS_NAME)
public class ProcessorServiceHandler implements EventHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(ProcessorServiceHandler.class);

    private final PersistenceService db;

    public ProcessorServiceHandler(PersistenceService db) {
        this.db = db;
    }


    /*
     * Change urgency from Medium to High
     * if title contains "urgent".
     *
     * CREATE
     */
    @Before(event = CqnService.EVENT_CREATE)
    public void ensureHighUrgencyOnCreate(List<Incidents> incidents) {

        for (Incidents incident : incidents) {

            String title = incident.getTitle();
            String urgency = incident.getUrgencyCode();


            if (title != null
                    && title.toLowerCase(Locale.ENGLISH).contains("urgent")
                    && "M".equals(urgency)) {

                incident.setUrgencyCode("H");

                logger.info(
                        "Changed urgency from MEDIUM to HIGH for incident '{}'.",
                        title);
            }
        }
    }



    /*
     * CREATE validations
     */
    @Before(event = CqnService.EVENT_CREATE)
    public void validateIncidentOnCreate(List<Incidents> incidents) {


        for (Incidents incident : incidents) {

            String title = incident.getTitle();


            // Title mandatory
            if (title == null ||
                    title.trim().isEmpty()) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Incident title is mandatory.");
            }


            // Minimum title length
            if (title.trim().length() < 5) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Incident title must contain at least 5 characters.");
            }


            // Only letters, numbers and spaces
            if (!title.matches("[a-zA-Z0-9 ]+")) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Special characters are not allowed in the incident title.");
            }


            // Cannot contain only numbers
            if (title.matches("\\d+")) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Incident title cannot contain only numbers.");
            }


            // Maximum length
            if (title.length() > 100) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Incident title cannot exceed 100 characters.");
            }



            // Customer mandatory
            if (incident.getCustomerId() == null ||
                    incident.getCustomerId().isBlank()) {

                throw new ServiceException(
                        ErrorStatuses.BAD_REQUEST,
                        "Customer is mandatory.");
            }



            // Duplicate title check
            if (incidentExists(title, null)) {

                throw new ServiceException(
                        ErrorStatuses.CONFLICT,
                        "An incident with the same title already exists.");
            }

        }
    }





    /*
     * Change urgency from Medium to High
     * if title contains "urgent".
     *
     * UPDATE
     */
    @Before(event = CqnService.EVENT_UPDATE)
    public void ensureHighUrgencyOnUpdate(List<Incidents> incidents) {


        for (Incidents incident : incidents) {


            String title = incident.getTitle();
            String urgency = incident.getUrgencyCode();



            if (title != null
                    && title.toLowerCase(Locale.ENGLISH).contains("urgent")
                    && "M".equals(urgency)) {


                incident.setUrgencyCode("H");


                logger.info(
                        "Changed urgency from MEDIUM to HIGH for incident '{}'.",
                        title);

            }
        }
    }





    /*
     * UPDATE validations
     */
    @Before(event = CqnService.EVENT_UPDATE)
    public void validateIncidentOnUpdate(Incidents incident) {


        String title = incident.getTitle();



        // Title mandatory
        if (title == null ||
                title.trim().isEmpty()) {

            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Incident title is mandatory.");
        }



        // Minimum length
        if (title.trim().length() < 5) {

            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Incident title must contain at least 5 characters.");
        }



        // Only letters numbers spaces
        if (!title.matches("[a-zA-Z0-9 ]+")) {

            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Special characters are not allowed in the incident title.");
        }



        // Cannot contain only numbers
        if (title.matches("\\d+")) {

            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Incident title cannot contain only numbers.");
        }



        // Maximum length
        if (title.length() > 100) {

            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Incident title cannot exceed 100 characters.");
        }



        // Customer validation
        if (incident.getCustomerId() == null ||
                incident.getCustomerId().isBlank()) {


            throw new ServiceException(
                    ErrorStatuses.BAD_REQUEST,
                    "Customer is mandatory.");
        }



        // Duplicate check excluding same record
        if (incidentExists(title, incident.getId())) {


            throw new ServiceException(
                    ErrorStatuses.CONFLICT,
                    "An incident with the same title already exists.");

        }

    }





    /*
     * Handler to avoid updating a "closed" incident.
     */
    @Before(event = CqnService.EVENT_UPDATE)
    public void ensureNoUpdateOnClosedIncidents(Incidents incident) {


        Incidents in = db.run(
                Select.from(Incidents_.class)
                        .where(i -> i.ID().eq(incident.getId()))
        ).single(Incidents.class);



        if ("C".equals(in.getStatusCode())) {

            throw new ServiceException(
                    ErrorStatuses.CONFLICT,
                    "Can't modify a closed incident"
            );
        }
    }




    /*
     * Duplicate title check
     *
     * id = null during CREATE
     * id contains current record during UPDATE
     */
    private boolean incidentExists(String title, String id) {


        List<Incidents> incidents = db.run(
                Select.from(Incidents_.class)
                        .where(i ->
                                i.title().eq(title)
                                .and(id == null ?
                                        i.ID().isNotNull()
                                        :
                                        i.ID().ne(id)))
        ).listOf(Incidents.class);



        return !incidents.isEmpty();
    }
}