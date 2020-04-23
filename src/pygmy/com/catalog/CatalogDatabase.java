package pygmy.com.catalog;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javassist.compiler.ast.StringL;
import pygmy.com.wal.CatalogWriteAheadLogger;

public class CatalogDatabase {

    // acquire this lock while accessing critical database sections
    Object dbLock = null;

    Book[] books = null;
    int numBooks;

    CatalogWriteAheadLogger cWALogger = null;

    public CatalogDatabase(CatalogWriteAheadLogger logger) {
        numBooks = 0;
        dbLock = new Object();
        cWALogger = logger;
    }

    public CatalogDatabase() {
        numBooks = 0;
        dbLock = new Object();
    }

    public boolean init(String initialInventoryPath) {

        synchronized (dbLock) {

            BufferedReader reader = null;
            try {
                if (!cWALogger.writeInitDB(initialInventoryPath, System.currentTimeMillis())) {
                    return false;
                }
                reader = new BufferedReader(new FileReader(initialInventoryPath));

                // read how many different books
                if ((numBooks = Integer.parseInt(reader.readLine())) > 0) {
                    books = new Book[numBooks];

                    // read the books one by one
                    int i = 0;
                    while (i < numBooks) {

                        // the first line of each book-entry contains the book's name
                        String bookName = reader.readLine();

                        // the second line of each book-entry contains the topic of the book
                        String topicName = reader.readLine();

                        // the third line has the unique-id of the book
                        String uniqueId = reader.readLine();

                        // the fourth line has the cost of the book
                        int cost = Integer.parseInt(reader.readLine());

                        // the fifth line has the number of this book in stock
                        int count = Integer.parseInt(reader.readLine());

                        books[i] = new Book(bookName, topicName, uniqueId, cost, count);

                        System.out.println(books[i].JSONifySelf().toString(2));

                        i++;
                    }
                }

                reader.close();
            } catch (FileNotFoundException e) {
                System.out.print(
                        CatalogServer.getTime() + "File not found at " + initialInventoryPath
                                + "!");
                e.printStackTrace();
            } catch (NumberFormatException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return true;
    }

    public ArrayList<Book> searchByTopic(String topic) {

        cWALogger.writeQueryTopic(topic, System.currentTimeMillis());

        ArrayList<Book> result = new ArrayList<Book>();

        // no need of acquiring lock to just search the database
        for (Book book : books) {
            if (book.getTopic().equals(topic)) {
                result.add(book);
            }
        }

        return result;
    }

    public Book searchById(String bookId) {

        cWALogger.writeQueryBook(bookId, System.currentTimeMillis());

        // no need of acquiring lock to just search the database
        for (Book book : books) {
            if (book.getUniqueId().equals(bookId)) {
                return book;
            }
        }
        return null;
    }

    // returns a 0+ count if the update was successful
    // else returns a negative count
    public String updateCountById(String bookId, int updateBy, String orderId) {

        // acquire the lock since we are updating the stock
        synchronized (dbLock) {
            long timestamp = System.currentTimeMillis();
            for (Book book : books) {
                if (book.getUniqueId().equals(bookId)) {
                    // if this is a decrement op,
                    // do we enough count in stock?
                    if ((book.getStock() + updateBy) >= 0) {

                        if (!cWALogger.writeUpdate(bookId, updateBy, timestamp, orderId)) {
                            return Integer.MIN_VALUE + "_" + Long.MIN_VALUE;
                        }

                        book.setStock(book.getStock() + updateBy);
                        return book.getStock() + "_" + timestamp;
                    } else {
                        return (book.getStock() + updateBy) + "_" + timestamp;
                    }
                }
            }

            return Integer.MIN_VALUE + "_" + timestamp;
        }
    }

    public void replayFromWAL(String walFile) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(walFile));
            String line = null;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                String action = parts[0];
                switch (action) {

                case "INITDB":
                    init(parts[1]); // init the DB
                    break;

                case "UPDATE":
                    String bookId = parts[1];
                    int updateBy = Integer.parseInt(parts[2]);
                    String orderId = parts[4];
                    updateCountById(bookId, updateBy, orderId);
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void prettyPrintCatalog() {
        for (Book book : books) {
            System.out.println(book.JSONifySelf().toString(2));
        }
    }

}
