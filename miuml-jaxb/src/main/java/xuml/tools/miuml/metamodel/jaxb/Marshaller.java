package xuml.tools.miuml.metamodel.jaxb;

import java.io.InputStream;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;

/**
 * Uunmarshals {@link Domains}.
 * 
 * @author dave
 * 
 */
public class Marshaller {

    private Unmarshaller unmarshaller;

    /**
     * Constructor.
     */
    public Marshaller() {

        try {
            JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
            unmarshaller = context.createUnmarshaller();
            SchemaFactory sf = SchemaFactory
                    .newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(getClass().getResource("/miuml-metamodel.xsd"));
            unmarshaller.setSchema(schema);
            unmarshaller.setEventHandler(new ValidationEventHandler() {
                @Override
                public boolean handleEvent(ValidationEvent event) {
                    throw new RuntimeException(event.getMessage(), event.getLinkedException());
                }
            });
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unmarshals the xml in the {@link InputStream} to a {@link Domains}.
     * Throws a {@link RuntimeException} if anything goes wrong.
     * 
     * @param is
     * @return
     */
    public synchronized Domains unmarshal(InputStream is) {
        Preconditions.checkNotNull(is, "InputStream is null!");
        try {
            return unmarshaller.unmarshal(new StreamSource(is), Domains.class).getValue();
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

}