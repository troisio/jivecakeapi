import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Date;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateOpsImpl;

import com.jivecake.api.model.Application;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.service.PermissionService;

public class PermissionServiceTest extends DatastoreTest {
    private PermissionService permissionService;

    @Before
    public void before() {
        this.permissionService = new PermissionService(super.datastore);
    }

    @Test
    public void permissionCheckReturnsTrueOnEmptyEntities() {
        boolean hasPermission = this.permissionService.has(
            "user5000",
            Arrays.asList(),
            true,
            false
        );

        assertTrue(hasPermission);
    }

    @Test
    public void permissionCheckReturnsFalseOnPartialPermissions() {
        String userId = "user5000";

        Organization firstOrganization = new Organization();
        firstOrganization.id = new ObjectId();

        Organization secondOrganization = new Organization();
        secondOrganization.id = new ObjectId();

        Permission firstPermission = new Permission();
        firstPermission.read = true;
        firstPermission.objectClass = "Organization";
        firstPermission.objectId = firstOrganization.id;
        firstPermission.user_id = userId;

        Permission secondPermission = new Permission();
        secondPermission.read = false;
        secondPermission.objectClass = "Organization";
        secondPermission.objectId = secondOrganization.id;
        secondPermission.user_id = userId;

        this.datastore.save(
            Arrays.asList(
                firstOrganization,
                secondOrganization,
                firstPermission,
                secondPermission
            )
        );

        boolean hasPermission = this.permissionService.has(
            userId,
            Arrays.asList(firstOrganization, secondOrganization),
            true,
            false
        );

        assertFalse(hasPermission);
    }

    @Test
    public void permissionCheckReturnsTrueWhenUserHasPermissions() {
        String userId = "user5000";

        Organization firstOrganization = new Organization();
        firstOrganization.id = new ObjectId();

        Organization secondOrganization = new Organization();
        secondOrganization.id = new ObjectId();

        Application application = new Application();
        application.id = new ObjectId();

        Permission firstPermission = new Permission();
        firstPermission.read = true;
        firstPermission.objectClass = "Organization";
        firstPermission.objectId = firstOrganization.id;
        firstPermission.user_id = userId;

        Permission secondPermission = new Permission();
        secondPermission.read = true;
        secondPermission.objectClass = "Organization";
        secondPermission.objectId = secondOrganization.id;
        secondPermission.user_id = userId;

        Permission thirdPermission = new Permission();
        thirdPermission.read = true;
        thirdPermission.objectClass = "Application";
        thirdPermission.objectId = application.id;
        thirdPermission.user_id = userId;

        this.datastore.save(
            Arrays.asList(
                firstOrganization,
                secondOrganization,
                firstPermission,
                secondPermission,
                thirdPermission
            )
        );

        boolean hasPermission = this.permissionService.has(
            userId,
            Arrays.asList(firstOrganization, secondOrganization, application),
            true,
            false
        );

        assertTrue(hasPermission);
    }

    @Test
    public void upsertUpdatesOnUniqueCollision() {
        Permission original = new Permission();
        original.objectClass = "Organization";
        original.objectId = new ObjectId();
        original.user_id = "user_id";
        original.timeCreated = new Date();

        this.datastore.save(original);

        Permission newPermission = new Permission();
        newPermission.objectClass = "Organization";
        newPermission.objectId = new ObjectId();
        newPermission.user_id = "user_id";
        newPermission.write = true;
        newPermission.timeCreated = new Date();

        UpdateOperations<Permission> operations =  new UpdateOpsImpl<>(Permission.class, super.morphia.getMapper())
            .set("user_id", newPermission.user_id)
            .set("objectId", newPermission.objectId)
            .set("objectClass", newPermission.objectClass)
            .set("write", newPermission.write)
            .set("read", newPermission.read)
            .set("timeCreated", newPermission.timeCreated);

        Permission searchedPermission = this.datastore.findAndModify(
            this.datastore.createQuery(Permission.class)
                .field("objectClass").equal(original.objectClass)
                .field("objectId").equal(original.objectId)
                .field("user_id").equal(original.user_id),
            operations,
            new FindAndModifyOptions().upsert(true)
        );

        assertEquals(searchedPermission.write, newPermission.write);

        long permissionsCount = this.datastore.createQuery(Permission.class).count();
        assertEquals(1, permissionsCount);
    }
}