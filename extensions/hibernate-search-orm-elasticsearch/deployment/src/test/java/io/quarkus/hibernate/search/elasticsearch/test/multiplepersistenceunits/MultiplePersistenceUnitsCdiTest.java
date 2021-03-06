package io.quarkus.hibernate.search.elasticsearch.test.multiplepersistenceunits;

import static org.assertj.core.api.Assertions.assertThat;

import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import org.hibernate.search.mapper.orm.entity.SearchIndexedEntity;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.search.elasticsearch.test.multiplepersistenceunits.defaultpu.DefaultPUEntity;
import io.quarkus.hibernate.search.elasticsearch.test.multiplepersistenceunits.pu1.PU1Entity;
import io.quarkus.hibernate.search.elasticsearch.test.multiplepersistenceunits.pu2.PU2Entity;
import io.quarkus.test.QuarkusUnitTest;

public class MultiplePersistenceUnitsCdiTest {

    @RegisterExtension
    static QuarkusUnitTest runner = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(DefaultPUEntity.class.getPackage())
                    .addPackage(PU1Entity.class.getPackage())
                    .addPackage(PU2Entity.class.getPackage())
                    .addAsResource("application-multiple-persistence-units.properties", "application.properties"));

    @Inject
    SearchMapping defaultPUMapping;

    @Inject
    SearchSession defaultPUSession;

    @Inject
    @PersistenceUnit("pu1")
    SearchMapping pu1Mapping;

    @Inject
    @PersistenceUnit("pu1")
    SearchSession pu1Session;

    @Inject
    @PersistenceUnit("pu2")
    SearchMapping pu2Mapping;

    @Inject
    @PersistenceUnit("pu2")
    SearchSession pu2Session;

    @Inject
    UserTransaction transaction;

    @Test
    public void testDefaultMapping() {
        assertThat(defaultPUMapping.allIndexedEntities())
                .hasSize(1)
                .element(0)
                .returns(DefaultPUEntity.class, SearchIndexedEntity::javaClass);
    }

    @Test
    @ActivateRequestContext
    public void testDefaultSession() {
        DefaultPUEntity entity = new DefaultPUEntity("someText");
        inTransaction(() -> defaultPUSession.toEntityManager().persist(entity));
        inTransaction(() -> assertThat(defaultPUSession.search(DefaultPUEntity.class)
                .where(f -> f.matchAll())
                .fetchHits(20))
                        .hasSize(1)
                        .element(0)
                        .returns(entity.getId(), DefaultPUEntity::getId));
    }

    @Test
    public void testPU1Mapping() {
        assertThat(pu1Mapping.allIndexedEntities())
                .hasSize(1)
                .element(0)
                .returns(PU1Entity.class, SearchIndexedEntity::javaClass);
    }

    @Test
    @ActivateRequestContext
    public void testPU1Session() {
        PU1Entity entity = new PU1Entity("someText");
        inTransaction(() -> pu1Session.toEntityManager().persist(entity));
        inTransaction(() -> assertThat(pu1Session.search(PU1Entity.class)
                .where(f -> f.matchAll())
                .fetchHits(20))
                        .hasSize(1)
                        .element(0)
                        .returns(entity.getId(), PU1Entity::getId));
    }

    @Test
    public void testPU2Mapping() {
        assertThat(pu2Mapping.allIndexedEntities())
                .hasSize(1)
                .element(0)
                .returns(PU2Entity.class, SearchIndexedEntity::javaClass);
    }

    @Test
    @ActivateRequestContext
    public void testPU2Session() {
        PU2Entity entity = new PU2Entity("someText");
        inTransaction(() -> pu1Session.toEntityManager().persist(entity));
        inTransaction(() -> assertThat(pu1Session.search(PU2Entity.class)
                .where(f -> f.matchAll())
                .fetchHits(20))
                        .hasSize(1)
                        .element(0)
                        .returns(entity.getId(), PU2Entity::getId));
    }

    private void inTransaction(Runnable runnable) {
        try {
            transaction.begin();
            try {
                runnable.run();
                transaction.commit();
            } catch (Exception e) {
                transaction.rollback();
            }
        } catch (SystemException | NotSupportedException e) {
            throw new IllegalStateException("Transaction exception", e);
        }
    }
}
