import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;

public class TextEditor extends JFrame {
    //определяем константы для рисования
    private final int BUTTON_SIZE = 15;
    private final int AREA_WIDTH = 600;
    private final int AREA_HEIGHT = 450;
    private final int FIELD_HEIGHT = 26;
    private final int FIELD_COLUMNS = 20;
    private final int TEXT_ROWS = 20;
    private final int TEXT_COLUMNS = 56;

    private int beginIndex = 0; // индекс, с которого начинаем поиск
    private File selectedFile; // текущий файл
    private String searchText = ""; // текст, который ищем в файле
    private Matcher matcher; //шаблон для регулярных выражений
    private List<Integer> indices;  // индексы совпадений подстроки в тексте
    private int cur = 0; // текущий индекс
    private boolean legalRegex; //  разбираемо ли регулярное выражение
    private Thread worker; // нить, обрабатывающая текст

    public TextEditor() {
        setTitle("The first stage");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AREA_WIDTH, AREA_HEIGHT);
        setVisible(true);

        //все объекты будем добавлять на панель
        JPanel panel = new JPanel();

        //создаем поле для поиска по файлу
        JTextField searchField = new JTextField(FIELD_COLUMNS);
        searchField.setPreferredSize(new Dimension(0, FIELD_HEIGHT));
        searchField.setName("SearchField");

        //создаем поле для текста содержимого файла
        JTextArea textArea = new JTextArea(TEXT_ROWS, TEXT_COLUMNS);
        textArea.setName("TextArea");

        //создаем FileChooser, который изначально указывает на домашнюю директорию
        JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooser.setName("FileChooser");

        //создаем кнопку загрузки файла
        ImageIcon loadIcon = new ImageIcon(new ImageIcon("loadIcon.png").getImage().getScaledInstance(BUTTON_SIZE, BUTTON_SIZE,
                java.awt.Image.SCALE_SMOOTH ));
        JButton openButton = new JButton(loadIcon);
        openButton.setName("OpenButton");
        openButton.addActionListener(actionEvent -> {
            fileChooser.showOpenDialog(null);
            selectedFile = fileChooser.getSelectedFile();
            try (FileReader reader = new FileReader(selectedFile)) {
                textArea.read(reader, null);
            } catch (Exception e) {
                textArea.setText("");
            }
        });

        //оборачиваем поле в ScrollPane для того, чтобы можно было пролистывать текст
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setName("ScrollPane");

        //создаем кнопку сохранения файла
        ImageIcon saveIcon = new ImageIcon(new ImageIcon("saveIcon.png").getImage().getScaledInstance(BUTTON_SIZE, BUTTON_SIZE,
                java.awt.Image.SCALE_SMOOTH ));
        JButton saveButton = new JButton(saveIcon);
        saveButton.setName("SaveButton");
        saveButton.addActionListener(actionEvent -> {
            fileChooser.showOpenDialog(null);
            File saveFile = fileChooser.getSelectedFile();
            try (FileWriter writer = new FileWriter(saveFile)) {
                textArea.write(writer);
            } catch (Exception e) {
            }
        });

        //создаем checkBox, который отвечает за использование/неиспользование регулярных выражений
        JCheckBox checkBox = new JCheckBox("Use regex");
        checkBox.setName("UseRegExCheckbox");

        //создаем кнопку поиска в тексте
        ImageIcon searchIcon = new ImageIcon(new ImageIcon("searchIcon.png").getImage().getScaledInstance(BUTTON_SIZE, BUTTON_SIZE,
                java.awt.Image.SCALE_SMOOTH ));
        JButton searchButton = new JButton(searchIcon);
        searchButton.setName("StartSearchButton");
        searchButton.addActionListener(actionEvent -> {
            searchText = searchField.getText();
            legalRegex = true;
            try {
                Pattern pattern = Pattern.compile(searchText);
                matcher = pattern.matcher(textArea.getText()); //сделать в нити
                worker = new Thread (() -> {
                    indices = new ArrayList<Integer>();
                    matcher.reset();  // так как вызов matcher.find(0) может произойти раньше, необходимо перезагрузить matcher
                    while (matcher.find()) {
                        indices.add(matcher.start());
                    }
                });
                worker.start();
                cur = 0;
                if(checkBox.isSelected()) { // случай регулярного выражения
                    matcher.find(0); // вызываем, чтобы узнать длину регулярного выражения
                    textArea.setCaretPosition(matcher.start() + matcher.group().length()); // выделяем регулярное выражение
                    textArea.select(matcher.start(), matcher.start() + matcher.group().length());
                    textArea.grabFocus();
                }
            } catch(Exception e) {
                legalRegex = false;
            }
            if(!checkBox.isSelected()) { // случай обычного поиска
                int index = textArea.getText().indexOf(searchText, 0);
                if (index != -1) {
                    beginIndex = index + 1;
                    textArea.setCaretPosition(index + searchText.length());
                    textArea.select(index, index + searchText.length());
                    textArea.grabFocus();
                }
            }
        });

        //создаем кнопку предыдущего совпадения
        ImageIcon prevIcon = new ImageIcon(new ImageIcon("prevIcon.png").getImage().getScaledInstance(BUTTON_SIZE, BUTTON_SIZE,
                java.awt.Image.SCALE_SMOOTH ));
        JButton prevButton = new JButton(prevIcon);
        prevButton.setName("PreviousMatchButton");
        prevButton.addActionListener(actionEvent -> {
            if(checkBox.isSelected()) { // случай регулярного выражения
                try {
                    worker.join(); // нужно дождаться обработки текста
                } catch (Exception e) {
                }
                if (legalRegex && indices.size() > 0) {
                    cur--;
                    if (cur <= -1) {
                        cur = indices.size() - 1;
                    }
                    matcher.find(cur); // вызываем, чтобы узнать длину регулярного выражения
                    textArea.setCaretPosition(indices.get(cur) + matcher.group().length()); // выделяем регулярное выражение
                    textArea.select(indices.get(cur), indices.get(cur) + matcher.group().length());
                    textArea.grabFocus();
                }
            } else {  // случай обычного поиска
                if (beginIndex == 0) { // если стоим на начале, то начинаем искать с конца
                    beginIndex = textArea.getText().length() + 1;
                }
                int index = textArea.getText().substring(0, beginIndex - 1).lastIndexOf(searchText);
                if (index == -1) { // зацикливаем поиск: если не нашли позади совпадения, ищем с конца
                    beginIndex = textArea.getText().length() + 1;
                    index = textArea.getText().substring(0, beginIndex - 1).lastIndexOf(searchText);
                }
                if (index != -1) {
                    beginIndex = index + 1;
                    textArea.setCaretPosition(index + searchText.length());
                    textArea.select(index, index + searchText.length());
                    textArea.grabFocus();
                }
            }
        });

        //создаем кнопку следующего совпадения
        ImageIcon nextIcon = new ImageIcon(new ImageIcon("nextIcon.png").getImage().getScaledInstance(BUTTON_SIZE, BUTTON_SIZE,
                java.awt.Image.SCALE_SMOOTH ));
        JButton nextButton = new JButton(nextIcon);
        nextButton.setName("NextMatchButton");
        nextButton.addActionListener(actionEvent -> {
            if(checkBox.isSelected()) { // случай регулярного выражения
                try {
                    worker.join();
                } catch (Exception e) {
                }
                if (legalRegex && indices.size() > 0) {
                    cur++;
                    if (cur == indices.size()) {
                        cur = 0;
                    }
                    matcher.find(cur); // вызываем, чтобы узнать длину регулярного выражения
                    textArea.setCaretPosition(indices.get(cur) + matcher.group().length()); // выделяем регулярное выражение
                    textArea.select(indices.get(cur), indices.get(cur) + matcher.group().length());
                    textArea.grabFocus();
                }
            } else { // случай обычного поиска
                int index = textArea.getText().indexOf(searchText, beginIndex);
                if (index == -1) {  // зацикливаем поиск: если не нашли впереди совпадения, возвращаемся в начало
                    beginIndex = 0;
                    index = textArea.getText().indexOf(searchText, beginIndex);
                }
                if (index != -1) {
                    beginIndex = index + 1;
                    textArea.setCaretPosition(index + searchText.length());
                    textArea.select(index, index + searchText.length());
                    textArea.grabFocus();
                }
            }
        });

        //создаем меню-бар
        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        //создаем меню File и Search
        JMenu fileMenu = new JMenu("File");
        fileMenu.setName("MenuFile");
        fileMenu.setMnemonic(KeyEvent.VK_F);
        JMenu searchMenu = new JMenu("Search");
        searchMenu.setName("MenuSearch");
        searchMenu.setMnemonic(KeyEvent.VK_S);

        //добавляем поля меню File
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setName("MenuSave");
        saveItem.addActionListener(saveButton.getActionListeners()[0]);
        fileMenu.add(saveItem);

        JMenuItem openItem = new JMenuItem("Open");
        openItem.setName("MenuOpen");
        openItem.addActionListener(openButton.getActionListeners()[0]);
        fileMenu.add(openItem);

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setName("MenuExit");
        exitItem.addActionListener(actionEvent -> {
            dispose();
        });
        fileMenu.add(exitItem);

        //добавляем поля меню Search
        JMenuItem startItem = new JMenuItem("Start");
        startItem.setName("MenuStartSearch");
        startItem.addActionListener(searchButton.getActionListeners()[0]);
        searchMenu.add(startItem);

        JMenuItem previousItem = new JMenuItem("Previous");
        previousItem.setName("MenuPreviousMatch");
        previousItem.addActionListener(prevButton.getActionListeners()[0]);
        searchMenu.add(previousItem);

        JMenuItem nextItem = new JMenuItem("Next");
        nextItem.setName("MenuNextMatch");
        nextItem.addActionListener(nextButton.getActionListeners()[0]);
        searchMenu.add(nextItem);

        JMenuItem useItem = new JMenuItem("Use");
        useItem.setName("MenuUseRegExp");
        useItem.addActionListener(actionEvent -> {
            if(checkBox.isSelected()) {
                checkBox.setSelected(false);
            } else {
                checkBox.setSelected(true);
            }
        });
        searchMenu.add(useItem);

        //прикрепляем меню File и Search
        menuBar.add(fileMenu);
        menuBar.add(searchMenu);

        //прикрепляем все объекты к панели
        panel.add(saveButton);
        panel.add(openButton);
        panel.add(searchField);
        panel.add(searchButton);
        panel.add(prevButton);
        panel.add(nextButton);
        panel.add(checkBox);
        panel.add(scrollPane);
        add(panel);
    }
}
