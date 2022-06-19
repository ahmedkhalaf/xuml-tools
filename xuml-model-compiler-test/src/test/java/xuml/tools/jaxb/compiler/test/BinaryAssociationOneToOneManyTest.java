package xuml.tools.jaxb.compiler.test;

import static org.junit.Assert.assertEquals;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import one_to_one_many.A;
import one_to_one_many.A.AId;
import one_to_one_many.B;
import one_to_one_many.B.BId;
import one_to_one_many.Context;
import xuml.tools.model.compiler.runtime.RelationshipNotEstablishedException;

public class BinaryAssociationOneToOneManyTest {

    @BeforeClass
    public static void setup() {
        EntityManagerFactory emf = PersistenceHelper.createEmf("one-to-one-many");
        Context.setEntityManagerFactory(emf, 10);
    }

    @AfterClass
    public static void shutdown() {
        Context.stop();
    }

    @Test(expected = RelationshipNotEstablishedException.class)
    public void testCreateAWithoutB() {

        EntityManager em = Context.createEntityManager();
        em.getTransaction().begin();
        A.create(new A.AId("hello", "there")).persist(em);
        em.getTransaction().commit();
        em.close();
    }

    @Test(expected = RelationshipNotEstablishedException.class)
    public void testCannotCreateBWithoutA() {

        EntityManager em = Context.createEntityManager();
        em.getTransaction().begin();
        try {
            B.create(new BId("some", "thing")).persist(em);
        } finally {
            em.getTransaction().rollback();
            em.close();
        }
    }

    @Test
    public void testCreateAWithMultipleBAndIsPersistedProperly() {
        {
            EntityManager em = Context.createEntityManager();
            em.getTransaction().begin();
            A a = A.create(new AId("boo", "baa"));
            B b = B.create(new BId("some2", "thing2"));
            B b2 = B.create(new BId("some3", "thing3"));
            a.getB_R1().add(b);
            b.setA_R1(a);
            a.getB_R1().add(b2);
            b2.setA_R1(a);
            a.persist(em);
            b.persist(em);
            b2.persist(em);
            em.getTransaction().commit();
            em.close();
        }
        {
            EntityManager em = Context.createEntityManager();
            em.getTransaction().begin();
            A a2 = em.find(A.class, new A.AId("boo", "baa"));
            assertEquals(2, a2.getB_R1().size());
            em.getTransaction().commit();
            em.close();
        }
    }

}
