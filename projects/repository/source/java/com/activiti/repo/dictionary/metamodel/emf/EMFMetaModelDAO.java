package com.activiti.repo.dictionary.metamodel.emf;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import com.activiti.repo.dictionary.DictionaryException;
import com.activiti.repo.dictionary.metamodel.M2Aspect;
import com.activiti.repo.dictionary.metamodel.M2Class;
import com.activiti.repo.dictionary.metamodel.M2Property;
import com.activiti.repo.dictionary.metamodel.M2PropertyType;
import com.activiti.repo.dictionary.metamodel.M2Type;
import com.activiti.repo.dictionary.metamodel.MetaModelDAO;
import com.activiti.repo.dictionary.metamodel.emf.impl.EmfPackageImpl;
import com.activiti.repo.ref.QName;


public class EMFMetaModelDAO implements MetaModelDAO
{
    private static final String DEFAULT_RESOURCEURI = "metamodel.xml";

    private String resourceURI = DEFAULT_RESOURCEURI;
    
    private Resource resource = null;

    private Map classIndex;
    
    
    public void setResourceURI(String resourceURI)
    {
        this.resourceURI = resourceURI;
    }
    

    public void initCreate()
    {
        initEMF();
        
        // Perform Validation
        URI uri;
        try
        {
            uri = URI.createFileURI(resourceURI);
        }
        catch(IllegalArgumentException e)
        {
            throw new DictionaryException("Cannot create the resource " + resourceURI + " as it is not a file.");
        }
        
        // Create EMF Resource
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.setURIConverter(new EMFURIConverterImpl());
        resource = resourceSet.createResource(uri);
        
        // Initialise Object Index (for lookup)
        initIndex(resource);
    }

    
    public void init()
    {
        initEMF();
        
        // Load EMF Resource
        URI uri = URI.createURI(resourceURI);
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.setURIConverter(new EMFURIConverterImpl());
        resource = resourceSet.getResource(uri, true);
        
        // Initialise Object Index (for lookup)
        initIndex(resource);
    }


    private void initEMF()
    {
        // Initialise EMF Package
        EmfPackageImpl.init();

        // Register Resource Factories for classpath protocol and xml
        Resource.Factory.Registry reg = Resource.Factory.Registry.INSTANCE;
        Map protocol = reg.getProtocolToFactoryMap();
        protocol.put("file", new XMIResourceFactoryImpl());
        protocol.put("classpath", new XMIResourceFactoryImpl());
        Map ext = reg.getExtensionToFactoryMap();
        ext.put("xml", new XMIResourceFactoryImpl());
    }


    private void initIndex(Resource resource)
    {
        classIndex = new HashMap();
        List objects = resource.getContents();
        for (Iterator iter = objects.iterator(); iter.hasNext(); /**/)
        {
            EObject eObject = (EObject)iter.next();
            indexObject(eObject);
        }
    }
    
    
    private void indexObject(EObject object)
    {
        // Index Object
        if (object instanceof M2Type)
        {
            M2Type m2Type= (M2Type)object;
            getObjectIndex(EmfPackage.eINSTANCE.getEMFClass()).put(m2Type.getName(), m2Type);
            getObjectIndex(EmfPackage.eINSTANCE.getEMFType()).put(m2Type.getName(), m2Type);
        }
        else if (object instanceof M2Aspect)
        {
            M2Aspect m2Aspect = (M2Aspect)object;
            getObjectIndex(EmfPackage.eINSTANCE.getEMFClass()).put(m2Aspect.getName(), m2Aspect);
            getObjectIndex(EmfPackage.eINSTANCE.getEMFAspect()).put(m2Aspect.getName(), m2Aspect);
        }
        else if (object instanceof M2PropertyType)
        {
            M2PropertyType m2PropertyType= (M2PropertyType)object;
            getObjectIndex(EmfPackage.eINSTANCE.getEMFPropertyType()).put(m2PropertyType.getName(), m2PropertyType);
        }
    }

    
    private Map getObjectIndex(EClass eClass)
    {
        Map objectIndex = (Map)classIndex.get(eClass);
        if (objectIndex == null)
        {
            objectIndex = new HashMap();
            classIndex.put(eClass, objectIndex);
        }
        return objectIndex; 
    }

    
    public Collection getTypes()
    {
        Map objectIndex = (Map)classIndex.get(EmfPackage.eINSTANCE.getEMFType());
        return objectIndex == null ? null : objectIndex.keySet();
    }


    public M2Class getClass(QName className)
    {
        Map objectIndex = (Map)classIndex.get(EmfPackage.eINSTANCE.getEMFClass()); 
        return objectIndex == null ? null : (M2Class)objectIndex.get(className);
    }

    
    public M2Type getType(QName typeName)
    {
        Map objectIndex = (Map)classIndex.get(EmfPackage.eINSTANCE.getEMFType()); 
        return objectIndex == null ? null : (M2Type)objectIndex.get(typeName);
    }


    public M2Aspect getAspect(QName aspectName)
    {
        Map objectIndex = (Map)classIndex.get(EmfPackage.eINSTANCE.getEMFAspect()); 
        return objectIndex == null ? null : (M2Aspect)objectIndex.get(aspectName);
    }
        
    
    public M2Property getProperty(QName className, String propertyName)
    {
        M2Class m2Class = getClass(className);
        if (m2Class != null)
        {
            List properties = m2Class.getProperties();
            Iterator iter = properties.iterator();
            while (iter.hasNext())
            {
                M2Property property = (M2Property)iter.next();
                if (property.getName().equals(propertyName))
                {
                    return property;
                }
            }
        }
        return null;
    }


    public M2PropertyType getPropertyType(QName propertyType)
    {
        Map idIndex = (Map)classIndex.get(EmfPackage.eINSTANCE.getEMFPropertyType()); 
        return idIndex == null ? null : (M2PropertyType)idIndex.get(propertyType);
    }
    
    
    public M2PropertyType createPropertyType(QName typeName)
    {
        EmfFactory factory = EmfFactory.eINSTANCE;
        M2PropertyType type = (M2PropertyType)factory.createEMFPropertyType();
        type.setName(typeName);
        resource.getContents().add(type);
        indexObject((EObject)type);
        return type;
    }
        
    
    public M2Type createType(QName typeName)
    {
        EmfFactory factory = EmfFactory.eINSTANCE;
        M2Type type = (M2Type)factory.createEMFType();
        type.setName(typeName);
        resource.getContents().add(type);
        indexObject((EObject)type);
        return type;
    }


    public M2Aspect createAspect(QName aspectName)
    {
        EmfFactory factory = EmfFactory.eINSTANCE;
        M2Aspect aspect = (M2Aspect)factory.createEMFAspect();
        aspect.setName(aspectName);
        resource.getContents().add(aspect);
        indexObject((EObject)aspect);
        return aspect;
    }

    
    public void save()
    {
        try
        {
            resource.save(null);
        }
        catch(IOException e)
        {
            throw new DictionaryException("Failed to save EMF Resource " + resourceURI, e);
        }
    }


    
}
