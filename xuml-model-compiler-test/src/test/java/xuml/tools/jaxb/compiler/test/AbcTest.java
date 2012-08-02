package xuml.tools.jaxb.compiler.test;

import static abc.A.Field.aOne;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import xuml.tools.model.compiler.runtime.actor.EntityActor;
import xuml.tools.model.compiler.runtime.actor.SignalProcessorListener;
import xuml.tools.model.compiler.runtime.actor.SignalProcessorListenerFactory;
import xuml.tools.model.compiler.runtime.message.Signal;
import abc.A;
import abc.A.AId;
import abc.A.BehaviourFactory;
import abc.A.Events.Create;
import abc.A.Events.StateSignature_DoneSomething;
import abc.Context;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.util.Duration;

public class AbcTest {

	/**
	 * Demonstrates the major aspects of using entities generated by
	 * xuml-model-compiler including reloading signals not fully processed at
	 * time of last shutdown, creation of entities and asynchronous signalling.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testCreateEntityManagerFactoryAndCreateAndSignalEntities()
			throws InterruptedException {

		// Note that the classes Context and A are generated from xml using
		// the xuml-tools-maven-plugin

		// see the setup() method below for how the Context is initialized.

		// send any signals not processed from last shutdown
		Context.sendSignalsInQueue();

		// create some entities (this happens synchronously)
		A a1 = Context.create(A.class, new A.Events.Create("value1.1",
				"value2.1", "1234"));
		assertEquals("value1.1", a1.getId().getAOne());
		A a2 = Context.create(A.class, new A.Events.Create("value1.2",
				"value2.2", "1234"));
		// use Builder pattern for this one (all Events and Id classes have
		// builders)
		A a3 = Context.create(A.class,
				A.Events.Create.builder().aOne("value1.3").aTwo("value2.3")
						.accountNumber("1234").build());

		// send asynchronous signals to a1 and a2
		a1.signal(new A.Events.SomethingDone(11));
		a2.signal(new A.Events.SomethingDone(12));
		// send asynchronous signal to a3 after a tiny delay (5ms)
		a3.signal(new A.Events.SomethingDone(13),
				Duration.create(5, TimeUnit.MILLISECONDS));

		// wait a bit for all signals to be processed
		Thread.sleep(2000);

		// Check the signals were processed

		// The load method reloads the entity using a fresh entity manager and
		// then closes the entity manager. As a consequence only non-proxied
		// fields of the entity will be retrievable using the load method.
		assertEquals("11", a1.load().getAThree());
		assertEquals("12", a2.load().getAThree());
		assertEquals("13", a3.load().getAThree());

		// Notice that all the above could be done without explicitly creating
		// EntityManagers at all. Nice!

		// Refresh the entities from the database using the
		// load(em) method and check the signals were processed
		EntityManager em = Context.createEntityManager();
		// note that the load method below does an em merge and refresh and
		// returns a new entity for use within the current entity manager
		assertEquals("11", a1.load(em).getAThree());
		assertEquals("12", a2.load(em).getAThree());
		assertEquals("13", a3.load(em).getAThree());

		// demonstrate select statements
		{
			List<A> list = A.select().many(em);
			assertTrue(list.size() >= 3);
		}

		{
			List<A> list = A.select(aOne.eq("value1.1")).many(em);
			assertEquals(1, list.size());
			assertNull(A.select(aOne.eq("zz")).any(em));
			assertNull(A.select(aOne.eq("zz")).one(em));
		}

		em.close();

	}

	/**
	 * Creates the database, initializes the Context and sets the static
	 * behaviour factory for A.
	 */
	@BeforeClass
	public static void setup() {

		// create the entity manager factory
		EntityManagerFactory emf = PersistenceHelper.createEmf("abc");

		// Intercept entity processing to log activity
		// set this before setting EntityManagerFactory
		Context.setEntityActorListenerFactory(createEntityActorListenerFactory());

		// pass the EntityManagerFactory to the generated xuml Context
		Context.setEntityManagerFactory(emf);

		// set the behaviour factory for the class A
		A.setBehaviourFactory(createBehaviourFactoryForA());
	}

	/**
	 * Stop the actor system and shutdown the database.
	 */
	@AfterClass
	public static void cleanup() {

		// shutdown the actor system
		Context.stop();

		// close the entity manager factory if desired
		Context.close();
	}

	/**
	 * Tests signal persistence.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testEntitiesAndSignalsArePersisted()
			throws InterruptedException {

		A a = Context.create(A.class, new A.Events.Create("value1.4",
				"value2.4", "1234"));
		// check that the entity was persisted
		{
			EntityManager em = Context.createEntityManager();
			Assert.assertNotNull(em.find(A.class, AId.builder()
					.aOne("value1.4").aTwo("value2.4").build()));
			em.close();
		}

		// test the reloading of a persisted signal to a1
		Context.persistSignal(a.getId(), A.class,
				new A.Events.SomethingDone(14));
		assertEquals(1, Context.sendSignalsInQueue());
		// wait a bit for all signals to be processed
		Thread.sleep(2000);

		// Refresh the entities from the database using the
		// load method and check the signals were processed
		EntityManager em = Context.createEntityManager();
		// note that the load method below does an em merge and refresh and
		// returns a new entity for use within the current entity manager
		assertEquals("14", a.load(em).getAThree());
		em.close();

	}

	/**
	 * Returns a {@link BehaviourFactory} for A.
	 * 
	 * @return
	 */
	private static BehaviourFactory createBehaviourFactoryForA() {
		return new A.BehaviourFactory() {
			@Override
			public A.Behaviour create(final A self) {
				return new A.Behaviour() {

					@Override
					public void onEntryHasStarted(Create event) {
						self.setId(AId.builder().aOne(event.getAOne())
								.aTwo(event.getATwo()).build());
						self.setAThree(event.getAccountNumber());
						System.out.println("created");
					}

					@Override
					public void onEntryDoneSomething(
							StateSignature_DoneSomething event) {
						// use the method chaining version of setAThree
						// (underscore appended)
						System.out.println(self.setAThree_(
								event.getTheCount() + "").getId());
						System.out.println("setting A.athree="
								+ self.getAThree() + " for " + self.getId());
						// demonstrate/unit test getting access to the current
						// entity manager when needed
						Object count = Context.em()
								.createQuery("select count(b) from B b")
								.getResultList();
						System.out.println("counted " + count + " B entities");

						// also demonstrate select many using a SelectBuilder
						List<A> list = A.select(aOne.eq("value1.1")).many();
						System.out.println("list size = " + list.size());
					}
				};
			}
		};
	}

	private static SignalProcessorListenerFactory createEntityActorListenerFactory() {
		return new SignalProcessorListenerFactory() {

			// use the same listener for all entities
			private final SignalProcessorListener listener = createEntityActorListener();

			@Override
			public SignalProcessorListener create(String entityUniqueId) {
				return listener;
			}
		};
	}

	/**
	 * This listener logs activity using the Akka actor system logger.
	 * 
	 * @return
	 */
	private static SignalProcessorListener createEntityActorListener() {
		return new SignalProcessorListener() {

			private int processed = 0;

			@Override
			public void beforeProcessing(Signal<?> signal, EntityActor actor) {
				LoggingAdapter log = Logging.getLogger(actor.getContext()
						.system(), this);
				log.info("before processing");
			}

			@Override
			public void afterProcessing(Signal<?> signal, EntityActor actor) {
				// count the number processed and log it
				processed++;
				LoggingAdapter log = Logging.getLogger(actor.getContext()
						.system(), this);
				log.info("after processing " + processed);
			}

			@Override
			public void failure(Signal<?> signal, Exception e, EntityActor actor) {
				LoggingAdapter log = Logging.getLogger(actor.getContext()
						.system(), this);
				log.error(e, e.getMessage());
			}
		};
	}

	/**
	 * Tests that an exception is thrown if the {@link BehaviourFactory} has not
	 * been set for a class with behaviour before attempting to instantiate an
	 * instance of it.
	 */
	@Test(expected = NullPointerException.class)
	public void testBehaviourNotSetForAThrowsException() {
		A.BehaviourFactory f = A.getBehaviourFactory();
		try {
			A.setBehaviourFactory(null);
			new A();
		} finally {
			A.setBehaviourFactory(f);
		}
	}

}
