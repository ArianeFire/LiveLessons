package folder;

import utils.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Represents the contents of a folder, which can include recursive
 * (sub)folders and/or documents.
 */
public class Folder 
       extends Dirent {
    /**
     * The list of subfolders contained in this folder.
     */
    private List<Dirent> mSubFolders;

    /**
     * The list of documents contained in this folder.
     */
    private List<Dirent> mDocuments;

    /**
     * The total number of entries in this recursively structured
     * folder.
     */
    private long mSize;

    /**
     * Constructor initializes the fields.
     */
    Folder() {
        mSubFolders = new ArrayList<>();
        mDocuments = new ArrayList<>();
    }
    
    /**
     * @return The list of subfolders in this folder
     */
    @Override
    public List<Dirent> getSubFolders() {
        return mSubFolders;
    }
    
    /**
     * @return The list of documents in this folder
     */
    @Override
    public List<Dirent> getDocuments() {
        return mDocuments;
    }

    /**
     * @return The total number of entries in this recursively
     * structured folder.
     */
    public long size() {
        return mSize;
    }

    /**
     * @return A spliterator for this class
     */
    public Spliterator<Dirent> spliterator() {
        return new RecursiveFolderSpliterator(this);
    }

    /**
     * @return A sequential stream containing all elements rooted at
     * this directory entry
     */
    @Override
    public Stream<Dirent> stream() {
        return StreamSupport.stream(spliterator(),
                                    false);
    }

    /**
     * @return A parallel stream containing all elements rooted at
     * this directory entry
     */
    @Override
    public Stream<Dirent> parallelStream() {
        return StreamSupport.stream(spliterator(),
                                    true);
    }

    /*
     * The following factory methods are used by clients of this
     * class.
     */

    /**
     * Factory method that creates a folder from the given @a file.
     *
     * @param file The file associated with the folder in the file system
     * @param parallel A flag that indicates whether to create the
     *                 folder sequentially or in parallel
     *
     * @return An open document
     */
    public static Dirent fromDirectory(File file,
                                       boolean parallel) {
        return fromDirectory(file.toPath(),
                             parallel);
    }

    /**
     * Factory method that creates a folder from the given @a file.
     *
     * @param rootPath The path of the folder in the file system
     * @param parallel A flag that indicates whether to create the
     *                 folder sequentially or in parallel
     *
     * @return An open document
     */
    public static Dirent fromDirectory(Path rootPath,
                                       boolean parallel) {
        // An exception adapter.
        Function<Path, Stream<Path>> getStream = ExceptionUtils
            .rethrowFunction(path
                             // List all subfolders and documents in
                             // just this folder.
                             -> Files.walk(path, 1));

        // Create a stream containing all the contents at the given
        // rootPath.
        Stream<Path> pathStream = getStream.apply(rootPath);

        // Convert the stream to parallel if directed.
        if (parallel)
            //noinspection ResultOfMethodCallIgnored
            pathStream.parallel();

        // Create a folder containing all the contents at the given
        // rootPath.
        Folder folder = pathStream
            // Eliminate rootPath to avoid infinite recursion.
            .filter(path -> !path.equals(rootPath))

            // Terminate the stream and create a Folder containing all
            // entries in this folder.
            .collect(FolderCollector.toFolder(parallel));

        // Set the path of the folder and compute the number of
        // subfolders and documents are rooted at this folder.
        folder.setPath(rootPath);
        folder.computeSize();

        // Return the folder.
        return folder;
    }

    /**
     * Determine how many subfolders and documents are rooted at this
     * folder.
     */
    private void computeSize() {
        long folderCount = getSubFolders()
            // Convert list to a stream.
            .stream()

            // Get the size of each subfolder.
            .mapToLong(subFolder -> ((Folder) subFolder).mSize)

            // Sub up the sizes of the subfolders.
            .sum();

        // Count the number of documents in this folder.
        long docCount = (long) getDocuments().size();

        // Update the field with the correct count.
        mSize = folderCount 
            + docCount
            // Add 1 to count this folder.
            + 1;
    }

    /*
     * The methods below are used by the FolderCollector.
     */

    /**
     * Add a new @a entry to the appropriate list of futures.
     */
    void addEntry(Path entry,
                  boolean parallel) {
        // This adapter simplifies exception handling.
        Function<Path, Dirent> getFolder = ExceptionUtils
            .rethrowFunction(file 
                             // Create a folder from a directory file.
                             -> Folder.fromDirectory(file,
                                                     parallel));

        // This adapter simplifies exception handling.
        // Create a document from a path.
        Function<Path, Dirent> getDocument = ExceptionUtils
            .rethrowFunction(Document::fromPath);

        // Add entry to the appropriate list.
        if (Files.isDirectory(entry))
            mSubFolders.add(getFolder.apply(entry));
        else
            mDocuments.add(getDocument.apply(entry));
    }

    /**
     * Merge contents of @a folder into contents of this folder.
     *
     * @param folder The folder to merge from
     * @return The merged result
     */
    Folder addAll(Folder folder) {
        // Update the lists.
        mSubFolders.addAll(folder.mSubFolders);
        mDocuments.addAll(folder.mDocuments);

        // Initialize the size.
        mSize = mSubFolders.size() + mDocuments.size();

        return this;
    }
}
