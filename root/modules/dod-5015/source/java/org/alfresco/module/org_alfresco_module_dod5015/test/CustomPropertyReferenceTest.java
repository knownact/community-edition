/*
 * Copyright (C) 2005-2009 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have received a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.module.org_alfresco_module_dod5015.test;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.module.org_alfresco_module_dod5015.CustomisableRmElement;
import org.alfresco.module.org_alfresco_module_dod5015.DOD5015Model;
import org.alfresco.module.org_alfresco_module_dod5015.RecordsManagementAdminService;
import org.alfresco.module.org_alfresco_module_dod5015.RecordsManagementModel;
import org.alfresco.module.org_alfresco_module_dod5015.action.RecordsManagementActionService;
import org.alfresco.module.org_alfresco_module_dod5015.action.impl.DefineCustomPropertyAction;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.AspectDefinition;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.view.ImporterService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.BaseSpringTest;

/**
 * This test class tests the definition and use of a custom property and a custom
 * reference at the Java services layer.
 * 
 * @author Neil McErlean
 */
public class CustomPropertyReferenceTest extends BaseSpringTest implements DOD5015Model
{    
	protected static StoreRef SPACES_STORE = new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "SpacesStore");
	
	private NodeRef filePlan;
	
    private ContentService contentService;
    private DictionaryService dictionaryService;
	private ImporterService importService;
    private NamespaceService namespaceService;
    private NodeService nodeService;
	private SearchService searchService;
    private RecordsManagementActionService rmActionService;
    private RecordsManagementAdminService rmAdminService;
	private TransactionService transactionService;
	
	private PermissionService permissionService;
	
	// example base test data for supplemental markings list (see also recordsModel.xml)
	protected final static String NOFORN     = "NOFORN";     // Not Releasable to Foreign Nationals/Governments/Non-US Citizens
	protected final static String NOCONTRACT = "NOCONTRACT"; // Not Releasable to Contractors or Contractor/Consultants
	protected final static String FOUO       = "FOUO";       // For Official Use Only 
	protected final static String FGI        = "FGI";        // Foreign Government Information
	
	@Override
	protected void onSetUpInTransaction() throws Exception 
	{
		super.onSetUpInTransaction();

        this.dictionaryService = (DictionaryService)this.applicationContext.getBean("DictionaryService");
        this.contentService = (ContentService)this.applicationContext.getBean("ContentService");
		this.importService = (ImporterService)this.applicationContext.getBean("importerComponent");
		this.namespaceService = (NamespaceService)this.applicationContext.getBean("NamespaceService");
        this.nodeService = (NodeService)this.applicationContext.getBean("NodeService");
        this.permissionService = (PermissionService)this.applicationContext.getBean("PermissionService");
        this.rmActionService = (RecordsManagementActionService)this.applicationContext.getBean("RecordsManagementActionService");
        this.rmAdminService = (RecordsManagementAdminService)this.applicationContext.getBean("RecordsManagementAdminService");
        this.searchService = (SearchService)this.applicationContext.getBean("SearchService");
		this.transactionService = (TransactionService)this.applicationContext.getBean("TransactionService");
		
		// Set the current security context as admin
		AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
		
		// Get the test data
		setUpTestData();
	}
	
	private void setUpTestData()
	{
	    // Don't reload the fileplan data on each test method.
	    if (retrieveJanuaryAISVitalFolders().size() != 1)
	    {
            filePlan = TestUtilities.loadFilePlanData(null, this.nodeService, this.importService, this.permissionService);
	    }
	}

    @Override
    protected void onTearDownInTransaction() throws Exception
    {
        try
        {
            UserTransaction txn = transactionService.getUserTransaction(false);
            txn.begin();
            this.nodeService.deleteNode(filePlan);
            txn.commit();
        }
        catch (Exception e)
        {
            // Nothing
            //System.out.println("DID NOT DELETE FILE PLAN!");
        }
    }
    
    public void testCreateAndUseCustomProperty() throws Exception
    {
        // Create the necessary test object in the db: a record.
        NodeRef recordFolder = retrievePreexistingRecordFolder();
        NodeRef testRecord = createRecord(recordFolder, "testRecord" + System.currentTimeMillis());
        
        setComplete();
        endTransaction();
        
        UserTransaction txn1 = transactionService.getUserTransaction(false);
        txn1.begin();

        declareRecord(testRecord);

        // Define a custom property.
        final String propName = "rmc:customProperty" + System.currentTimeMillis();

        Map <String, Serializable> params = new HashMap<String, Serializable>();
        params.put("name", propName);
        params.put("dataType", DataTypeDefinition.BOOLEAN);
        params.put(DefineCustomPropertyAction.PARAM_ELEMENT, "recordFolder");
        rmActionService.executeRecordsManagementAction("defineCustomProperty", params);
        
        // We need to commit the transaction to trigger behaviour that should reload the data dictionary model.
        txn1.commit();
        
        UserTransaction txn2 = transactionService.getUserTransaction(false);
        txn2.begin();
        
        // Confirm the custom property is included in the list from rmAdminService.
        final QName propQName = QName.createQName(propName, namespaceService);

        Map<QName, PropertyDefinition> customPropDefinitions = rmAdminService.getAvailableCustomProperties(CustomisableRmElement.RECORD_FOLDER);
        PropertyDefinition propDefn = customPropDefinitions.get(propQName);
        assertNotNull("Custom property definition from rmAdminService was null.", propDefn);
        assertEquals(propName, propDefn.getName().toPrefixString(namespaceService));
        assertEquals(DataTypeDefinition.BOOLEAN, propDefn.getDataType().getName());
        
        // Now we need to use the custom property.
        // So we apply the aspect containing it to our test record.
        Map<QName, Serializable> customPropValue = new HashMap<QName, Serializable>();
        customPropValue.put(propQName, true);
        QName aspectQName = QName.createQName("rmc:customRecordFolderProperties", namespaceService);
        nodeService.addAspect(testRecord, aspectQName, customPropValue);
        
        txn2.commit();
        
        // Read back the property value to make sure it was correctly applied.
        transactionService.getUserTransaction(true);
        Map<QName, Serializable> nodeProps = nodeService.getProperties(testRecord);
        Serializable testProperty = nodeProps.get(propQName);
        assertNotNull("The testProperty was null.", testProperty);
        
        boolean testPropertyValue = (Boolean)testProperty;
        assertEquals("The test property was not 'true'.", true, testPropertyValue);
        
        // Check that the property has appeared in the data dictionary
        final AspectDefinition customPropertiesAspect = dictionaryService.getAspect(aspectQName);
        assertNotNull(customPropertiesAspect);
        assertNotNull("The customProperty is not returned from the dictionaryService.",
                customPropertiesAspect.getProperties().get(propQName));
    }
    
    public void testCreateAndUseCustomChildReference() throws Exception
    {
        createAndUseCustomReference(true, "superseded", "superseding");
    }

    public void testCreateAndUseCustomNonChildReference() throws Exception
    {
    	createAndUseCustomReference(false, "supporting", "supported");
    }
    
	private void createAndUseCustomReference(boolean isChild, String srcRoleName, String targetRoleName) throws Exception
	{
		// Create the necessary test objects in the db: two records.
        NodeRef recordFolder = retrievePreexistingRecordFolder();
        NodeRef testRecord1 = createRecord(recordFolder, "testRecordA" + System.currentTimeMillis());
        NodeRef testRecord2 = createRecord(recordFolder, "testRecordB" + System.currentTimeMillis());
        
        setComplete();
        endTransaction();
        
        UserTransaction txn1 = transactionService.getUserTransaction(false);
        txn1.begin();

        declareRecord(testRecord1);
        declareRecord(testRecord2);

        // Define a custom reference.
        final String refDefinitionName = "rmc:customReference" + System.currentTimeMillis();

        Map <String, Serializable> params = new HashMap<String, Serializable>();
        params.put("name", refDefinitionName);
        params.put("isChild", isChild);
        params.put("sourceRoleName", srcRoleName);
        params.put("targetRoleName", targetRoleName);
        
        rmActionService.executeRecordsManagementAction("defineCustomAssociation", params);
        
        // We need to commit the transaction to trigger behaviour that should reload the data dictionary model.
        txn1.commit();
        
        UserTransaction txn2 = transactionService.getUserTransaction(false);
        txn2.begin();

        // Confirm the custom reference is included in the list from rmAdminService.
        final QName refDefinitionQName = QName.createQName(refDefinitionName, namespaceService);
        
        Map<QName, AssociationDefinition> customRefDefinitions = rmAdminService.getAvailableCustomReferences();
        AssociationDefinition retrievedRefDefn = customRefDefinitions.get(refDefinitionQName);
        assertNotNull("Custom reference definition from rmAdminService was null.", retrievedRefDefn);
        assertEquals(refDefinitionName, retrievedRefDefn.getName().toPrefixString());
        assertEquals(isChild, retrievedRefDefn.isChild());
        
        if (srcRoleName != null) assertEquals("rmc:" + srcRoleName, retrievedRefDefn.getSourceRoleName().toPrefixString()); //TODO Remove ifs
        if (targetRoleName != null) assertEquals("rmc:" + targetRoleName, retrievedRefDefn.getTargetRoleName().toPrefixString());
        
        // Now we need to use the custom reference.
        // So we apply the aspect containing it to our test records.
        
        QName assocsAspectQName = QName.createQName("rmc:customAssocs", namespaceService);
        nodeService.addAspect(testRecord1, assocsAspectQName, null);

        String assocInstanceQNameString = "rmc:" + targetRoleName;
		QName assocInstanceQName = QName.createQName(assocInstanceQNameString, namespaceService);
		if (isChild)
		{
			nodeService.addChild(testRecord1, testRecord2, refDefinitionQName, assocInstanceQName);
		}
		else
		{
			nodeService.createAssociation(testRecord1, testRecord2, refDefinitionQName);
		}
        
        txn2.commit();
        
        // Read back the reference value to make sure it was correctly applied.
        transactionService.getUserTransaction(true);
    	List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(testRecord1);
    	List<AssociationRef> retrievedAssocs = nodeService.getTargetAssocs(testRecord1, RegexQNamePattern.MATCH_ALL);
    	
    	Object newlyAddedRef = null;
    	if (isChild)
    	{
    		for (ChildAssociationRef caRef : childAssocs)
    		{
    			QName refInstanceQName = caRef.getQName();
    			if (assocInstanceQName.equals(refInstanceQName)) newlyAddedRef = caRef;
    		}
    	}
    	else
    	{
    		for (AssociationRef aRef : retrievedAssocs)
    		{
    			QName refQName = aRef.getTypeQName();
    			if (refDefinitionQName.equals(refQName)) newlyAddedRef = aRef;
    		}
    	}
    	assertNotNull("newlyAddedRef was null.", newlyAddedRef);
        
        // Check that the reference has appeared in the data dictionary
        AspectDefinition customAssocsAspect = dictionaryService.getAspect(assocsAspectQName);
        assertNotNull(customAssocsAspect);
        if (isChild)
        {
        	assertNotNull("The customReference is not returned from the dictionaryService.",
        			customAssocsAspect.getChildAssociations().get(refDefinitionQName));
        }
        else
        {
        	assertNotNull("The customReference is not returned from the dictionaryService.",
        			customAssocsAspect.getAssociations().get(refDefinitionQName));
        }
	}
    
    private NodeRef retrievePreexistingRecordFolder()
    {
        final List<NodeRef> resultNodeRefs = retrieveJanuaryAISVitalFolders();
        final int folderCount = resultNodeRefs.size();
//        assertTrue("There should only be one 'January AIS Audit Records' folder. Were " + folderCount, folderCount == 1);
        
        return resultNodeRefs.get(0);
    }

    private List<NodeRef> retrieveJanuaryAISVitalFolders()
    {
        String typeQuery = "TYPE:\"" + TYPE_RECORD_FOLDER + "\" AND @cm\\:name:\"January AIS Audit Records\"";
        ResultSet types = this.searchService.query(SPACES_STORE, SearchService.LANGUAGE_LUCENE, typeQuery);
        
        final List<NodeRef> resultNodeRefs = types.getNodeRefs();
        return resultNodeRefs;
    }

	private NodeRef createRecord(NodeRef recordFolder, String name)
	{
    	// Create the document
        Map<QName, Serializable> props = new HashMap<QName, Serializable>(1);
        props.put(ContentModel.PROP_NAME, name);
        NodeRef recordOne = this.nodeService.createNode(recordFolder, 
                                                        ContentModel.ASSOC_CONTAINS, 
                                                        QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name), 
                                                        ContentModel.TYPE_CONTENT).getChildRef();
        
        // Set the content
        ContentWriter writer = this.contentService.getWriter(recordOne, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
        writer.setEncoding("UTF-8");
        writer.putContent("There is some content in this record");
        
        return recordOne;
	}   
      
    private void declareRecord(NodeRef recordOne)
    {
        // Declare record
        //Map<QName, Serializable> propValues = this.nodeService.getProperties(recordOne);        
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_PUBLICATION_DATE, new Date());       
//        List<String> smList = new ArrayList<String>(2);
//        smList.add(FOUO);
//        smList.add(NOFORN);
//        propValues.put(RecordsManagementModel.PROP_SUPPLEMENTAL_MARKING_LIST, (Serializable)smList);        
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_MEDIA_TYPE, "mediaTypeValue"); 
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_FORMAT, "formatValue"); 
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_DATE_RECEIVED, new Date());       
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_ORIGINATOR, "origValue");
        this.nodeService.setProperty(recordOne, RecordsManagementModel.PROP_ORIGINATING_ORGANIZATION, "origOrgValue");
        this.nodeService.setProperty(recordOne, ContentModel.PROP_TITLE, "titleValue");
        this.rmActionService.executeRecordsManagementAction(recordOne, "declareRecord");        
	}
}
