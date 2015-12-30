
package com.android.repository.impl.generated.v1;

import javax.xml.bind.annotation.XmlRegistry;
import com.android.repository.api.Channel;
import com.android.repository.api.Dependency;
import com.android.repository.api.License;
import com.android.repository.api.Repository;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.LocalPackageImpl;
import com.android.repository.impl.meta.RemotePackageImpl;


/**
 * DO NOT EDIT
 * This file was generated by xjc from repo-common-01.xsd. Any changes will be lost upon recompilation of the schema.
 * See the schema file for instructions on running xjc.
 * 
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.android.repository.impl.generated.v1 package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
@SuppressWarnings("override")
public class ObjectFactory
    extends CommonFactory
{


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.android.repository.impl.generated.v1
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link RepositoryType }
     * 
     */
    public Repository createRepositoryType() {
        return new RepositoryType();
    }

    /**
     * Create an instance of {@link RemotePackage }
     * 
     */
    public RemotePackageImpl createRemotePackage() {
        return new RemotePackage();
    }

    /**
     * Create an instance of {@link LocalPackage }
     * 
     */
    public LocalPackageImpl createLocalPackage() {
        return new LocalPackage();
    }

    /**
     * Create an instance of {@link DependenciesType }
     * 
     */
    public com.android.repository.impl.meta.RepoPackageImpl.Dependencies createDependenciesType() {
        return new DependenciesType();
    }

    /**
     * Create an instance of {@link ArchivesType }
     * 
     */
    public com.android.repository.impl.meta.RepoPackageImpl.Archives createArchivesType() {
        return new ArchivesType();
    }

    /**
     * Create an instance of {@link LicenseRefType }
     * 
     */
    public com.android.repository.impl.meta.RepoPackageImpl.UsesLicense createLicenseRefType() {
        return new LicenseRefType();
    }

    /**
     * Create an instance of {@link DependencyType }
     * 
     */
    public Dependency createDependencyType() {
        return new DependencyType();
    }

    /**
     * Create an instance of {@link LicenseType }
     * 
     */
    public License createLicenseType() {
        return new LicenseType();
    }

    /**
     * Create an instance of {@link ArchiveType }
     * 
     */
    public Archive createArchiveType() {
        return new ArchiveType();
    }

    /**
     * Create an instance of {@link com.android.repository.impl.generated.v1.PatchesType }
     * 
     */
    public Archive.PatchesType createPatchesType() {
        return new com.android.repository.impl.generated.v1.PatchesType();
    }

    /**
     * Create an instance of {@link com.android.repository.impl.generated.v1.CompleteType }
     * 
     */
    public Archive.CompleteType createCompleteType() {
        return new com.android.repository.impl.generated.v1.CompleteType();
    }

    /**
     * Create an instance of {@link com.android.repository.impl.generated.v1.PatchType }
     * 
     */
    public Archive.PatchType createPatchType() {
        return new com.android.repository.impl.generated.v1.PatchType();
    }

    /**
     * Create an instance of {@link ChannelType }
     * 
     */
    public Channel createChannelType() {
        return new ChannelType();
    }

    /**
     * Create an instance of {@link ChannelRefType }
     * 
     */
    public RemotePackageImpl.ChannelRef createChannelRefType() {
        return new ChannelRefType();
    }

    /**
     * Create an instance of {@link com.android.repository.impl.generated.v1.RevisionType }
     * 
     */
    public com.android.repository.impl.meta.RevisionType createRevisionType() {
        return new com.android.repository.impl.generated.v1.RevisionType();
    }

}
