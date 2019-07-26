/*******************************************************************************
 * Copyright (c) 2011 Sean Muir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Sean Muir - initial API and implementation
 *
 * $Id$
 *******************************************************************************/
package gov.fhim.service.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.mapping.ecore2xml.Ecore2XMLPackage;
import org.eclipse.mdht.uml.fhir.transform.ModelExporter;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.PackageableElement;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.resource.UMLResource;
import org.eclipse.uml2.uml.util.UMLSwitch;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.util.FhirResourceFactoryImpl;

import ca.uhn.fhir.context.FhirContext;

public class UMLService {

	private static UMLService umlService = null;

	private FhirContext fhirContext = null;

	/**
	 * @param fhirContext2
	 */
	public UMLService(FhirContext fhirContext2) {
		fhirContext = fhirContext2;
	}

	static public UMLService INSTANCE(FhirContext fhirContext) {
		if (umlService == null) {
			umlService = new UMLService(fhirContext);
		}
		return umlService;
	}

	static Package umlPackage = null;

	Resource umlResource = null;

	ModelExporter me = new ModelExporter();

	public void saveUml(ServletContext sce) throws IOException {

		if (umlPackage == null) {
			return;
		}

		String tempPath = (String) sce.getAttribute("javax.servlet.context.tempdir");

		URI modelURI = URI.createFileURI(tempPath + "/FHIM.uml");

		umlResource.setURI(modelURI);

		umlResource.save(null);
	}

	public String addClass(ServletContext sce, StructureDefinition structureDefinition) throws IOException {

		if (umlPackage == null) {
			loadUml(sce);
		}

		Package praxisPackage = (Package) umlPackage.getPackagedElement("PRAXIS");

		if (praxisPackage == null) {
			praxisPackage = (Package) umlPackage.createPackagedElement("PRAXIS", UMLPackage.eINSTANCE.getPackage());
		}

		Class profileClass = praxisPackage.createOwnedClass(structureDefinition.getName(), false);

		for (ElementDefinition elementDefinition : structureDefinition.getSnapshot().getElement()) {
			profileClass.createOwnedAttribute(
				elementDefinition.getShort(),
				umlPackage.getOwnedType("string", true, UMLPackage.eINSTANCE.getPrimitiveType(), false));
		}

		return profileClass.getQualifiedName();

	}

	private StructureDefinition StructureDefinitionFromClass(Class theClass) {

		org.hl7.fhir.StructureDefinition sd = me.createStrucureDefinition(theClass);

		if (sd != null) {
			URI resourceURI = URI.createFileURI(theClass.getName() + ".xml");

			FhirResourceFactoryImpl fhirResourceFactory = new FhirResourceFactoryImpl();
			Resource resource = fhirResourceFactory.createResource(resourceURI);
			resource.getContents().add(sd);
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				resource.save(baos, null);
				System.out.println(baos.toString());
				IBaseResource structureDefinition = fhirContext.newXmlParser().parseResource(baos.toString());
				return (StructureDefinition) structureDefinition;

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return null;
	}

	public static Classifier getClassifierByName(Package basePackage, final String localName, final EClass eClass) {
		if (basePackage == null || localName == null) {
			return null;
		}

		Classifier classifier = null;

		UMLSwitch<Object> umlSwitch = new UMLSwitch<Object>() {
			@Override
			public Object caseClassifier(Classifier classifier) {
				System.out.println(classifier.getName());
				if (localName.equalsIgnoreCase(classifier.getName())) {
					if (eClass == null) {
						return classifier;
					} else {
						return eClass == classifier.eClass()
								? classifier
								: null;
					}
				} else {
					return null;
				}
			}

			@Override
			public Object casePackage(Package pkg) {
				Object result = null;
				for (NamedElement namedElement : pkg.getOwnedMembers()) {
					result = doSwitch(namedElement);
					if (result != null) {
						break;
					}
				}
				return result;
			}
		};

		classifier = (Classifier) umlSwitch.doSwitch(basePackage);

		return classifier;
	}

	public StructureDefinition getStructureDefinition(ServletContext sce, String umlClassName) throws IOException {

		if (umlPackage == null) {
			loadUml(sce);
		}

		Class theClass = (Class) getClassifierByName(umlPackage, umlClassName, null);
		return StructureDefinitionFromClass(theClass);

		// org.hl7.fhir.StructureDefinition sd = me.createStrucureDefinition(theClass);
		//
		// if (sd != null) {
		// URI resourceURI = URI.createFileURI(umlClassName + ".xml");
		//
		// FhirResourceFactoryImpl fhirResourceFactory = new FhirResourceFactoryImpl();
		// Resource resource = fhirResourceFactory.createResource(resourceURI);
		// resource.getContents().add(sd);
		// try {
		// ByteArrayOutputStream baos = new ByteArrayOutputStream();
		// resource.save(baos, null);
		// System.out.println(baos.toString());
		// IBaseResource structureDefinition = fhirContext.newXmlParser().parseResource(baos.toString());
		// return (StructureDefinition) structureDefinition;
		//
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// }

		// return null;

	}

	public void loadUml(ServletContext sce) throws IOException {

		if (umlPackage != null) {
			return;
		}

		ResourceSet resourceSet = new ResourceSetImpl();

		UMLPackage.eINSTANCE.eClass();
		// Initialize registry
		Registry packageRegistry = resourceSet.getPackageRegistry();
		packageRegistry.put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
		packageRegistry.put(Ecore2XMLPackage.eNS_URI, Ecore2XMLPackage.eINSTANCE);
		packageRegistry.put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);

		// Initialize pathmaps
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put(
			UMLResource.FILE_EXTENSION, UMLResource.Factory.INSTANCE);
		Map<URI, URI> uriMap = resourceSet.getURIConverter().getURIMap();

		URL umlPluginLocation = Package.class.getProtectionDomain().getCodeSource().getLocation();

		// Create and Add UML PathMaps
		URI uri = URI.createURI(umlPluginLocation.getPath());
		uriMap.put(URI.createURI(UMLResource.LIBRARIES_PATHMAP), uri.appendSegment("libraries").appendSegment(""));
		uriMap.put(URI.createURI(UMLResource.METAMODELS_PATHMAP), uri.appendSegment("metamodels").appendSegment(""));
		uriMap.put(URI.createURI(UMLResource.PROFILES_PATHMAP), uri.appendSegment("profiles").appendSegment(""));

		resourceSet.getURIConverter().getURIMap().putAll(EcorePlugin.computePlatformURIMap(false));

		// Open the model
		URI modelFile = URI.createFileURI("fhim.uml");

		umlResource = resourceSet.createResource(modelFile);

		umlResource.load(sce.getResourceAsStream("/WEB-INF/model/FHIM.uml"), null);

		// String tempDir = (String) sce.getAttribute("javax.servlet.context.tempdir");

		// resourceSet.getResource(sce.getResourceAsStream("/WEB-INF/maps.properties"), true);

		umlPackage = (Package) EcoreUtil.getObjectByType(umlResource.getContents(), UMLPackage.eINSTANCE.getPackage());

		if (umlPackage != null) {

			EcoreUtil.resolveAll(umlPackage);

			// for (PackageableElement p : umlPackage.getPackagedElements()) {
			// if (p instanceof Package) {
			// Package thePackage = (Package) p;
			// for (Type t : thePackage.getOwnedTypes()) {
			// System.out.println(t.getQualifiedName());
			// if (t instanceof org.eclipse.uml2.uml.Class) {
			// // org.hl7.fhir.StructureDefinition sd = me.createStrucureDefinition((Class) t);
			// //
			// // if (sd != null) {
			// // URI resourceURI = URI.createFileURI(t.getName() + ".xml");
			// //
			// // FhirResourceFactoryImpl fhirResourceFactory = new FhirResourceFactoryImpl();
			// // Resource resource = fhirResourceFactory.createResource(resourceURI);
			// // resource.getContents().add(sd);
			// // try {
			// //
			// // ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// // resource.save(baos, null);
			// // System.out.println(baos.toString());
			// //
			// // } catch (IOException e) {
			// //
			// // e.printStackTrace();
			// // }
			// // }
			//
			// }
			//
			// }
			// }
			//
			// }

		}

	}

	public List<StructureDefinition> getStructuredDefinitionsByPackage(ServletContext sce, String packageName)
			throws IOException {
		loadUml(sce);

		List<StructureDefinition> result = new ArrayList<StructureDefinition>();

		for (PackageableElement p : umlPackage.getPackagedElements()) {
			System.out.println(packageName);
			System.out.println(p.getName());
			if (p instanceof Package && packageName.equalsIgnoreCase(p.getName())) {
				Package thePackage = (Package) p;
				for (Type t : thePackage.getOwnedTypes()) {

					if (t instanceof org.eclipse.uml2.uml.Class) {
						result.add(StructureDefinitionFromClass((Class) t));
					}
				}
			}
		}

		return result;

	}

}
