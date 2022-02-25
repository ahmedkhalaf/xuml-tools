package xuml.tools.miuml.metamodel.extensions.jaxb;



import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.google.common.base.Preconditions;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventHandler;

/**
 * Marshalls and unmarshalls xuml-tools extensions to the miuml metamodel
 * schema.
 * 
 * @author dave
 * 
 */
public class Marshaller {

    private Unmarshaller unmarshaller;

    /**
     * Constructor.
     * 
     */
    public Marshaller() {

        try {
            JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
            unmarshaller = context.createUnmarshaller();
            SchemaFactory sf = SchemaFactory
                    .newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = sf.newSchema(
                    getClass().getResource("/xuml-tools-miuml-metamodel-extensions.xsd"));
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
     * Unmarshalls a {@link Node} to a JAXB object from the xuml-tools miuml
     * extensions schema.
     * 
     * @param node
     * @return
     * @throws JAXBException
     */
    public synchronized Object unmarshal(Node node) throws JAXBException {
        Preconditions.checkNotNull(node, "Node is null!");
        return unmarshaller.unmarshal(node);
    }

}