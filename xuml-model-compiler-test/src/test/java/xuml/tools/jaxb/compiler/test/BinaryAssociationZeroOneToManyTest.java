package xuml.tools.jaxb.compiler.test;

import static org.junit.Assert.assertEquals;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import zero_one_to_many.A;
import zero_one_to_many.A.AId;
import zero_one_to_many.B;
import zero_one_to_many.B.BId;
import zero_one_to_many.Context;

public class BinaryAssociationZeroOneToManyTest {

    @BeforeClass
    public static void setup() {
        EntityManagerFactory emf = PersistenceHelper.createEmf("zero-one-to-many");
        Context.setEntityManagerFactory(emf, 10);
    }

    @AfterClass
    public static void shutdown() {
        Context.close();
    }

    @Test
    public void testCreateAWithoutB() {

        EntityManager em = Context.createEntityManager();
        em.getTransaction().begin();
        A.create(new A.AId("hello", "there")).persist(em);
        em.getTransaction().commit();
        em.close();
    }

    @Test
    public void testCanCreateBWithoutA() {

        EntityManager em = Context.createEntityManager();
        em.getTransaction().begin();
        B.create(new BId("some", "thing")).persist(em);
        em.getTransaction().commit();
        em.close();
    }

    @Test
    public void testCreateAWithMultipleBAndIsPersistedProperly() {
        {
            EntityManager em = Context.createEntityManager();
            em.getTransaction().begin();
            A a = A.create(new AId("boo", "baa"));
            B b = B.create(new BId("some2", "thing2"));
            B b2 = B.create(new BId("some3", "thing3"));
            a.relateAcrossR1(b);
            a.relateAcrossR1(b2);
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
