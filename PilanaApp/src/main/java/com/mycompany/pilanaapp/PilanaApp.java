package com.mycompany.pilanaapp;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.*;

public class PilanaApp extends JFrame {

    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/pilana_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = ""; 

    private final String trenutnaUloga;
    private final DefaultTableModel tableModel;

    private JTextField txtKupac;
    private JComboBox<String> cbVrstaDrveta;
    private JTextField txtKolicina;
    private JTextField txtCijenaPoM3;
    private final JTable tabela;
    private final JButton btnObrisi;

    public PilanaApp(String uloga, String korisnickoIme) {
        this.trenutnaUloga = uloga;

        setTitle("Pilana - Narudžbe [" + korisnickoIme + " | Uloga: " + uloga + "]");
        setSize(900, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        
        JPanel panelUnos = new JPanel(new GridLayout(2, 5, 8, 8));
        panelUnos.setBorder(BorderFactory.createTitledBorder("Nova narudžba"));

        panelUnos.add(new JLabel("Kupac:"));
        panelUnos.add(new JLabel("Vrsta građe:"));
        panelUnos.add(new JLabel("Količina (m³):"));
        panelUnos.add(new JLabel("Cijena po m³ (KM):"));
        panelUnos.add(new JLabel(""));

        txtKupac = new JTextField();
        cbVrstaDrveta = new JComboBox<>(new String[]{"Hrastova daska", "Čamova greda", "Bukvini elementi", "Jelova letva"});
        txtKolicina = new JTextField();
        txtCijenaPoM3 = new JTextField();

        JButton btnDodaj = new JButton("Sačuvaj u bazu");
        btnDodaj.setBackground(new Color(50, 130, 60));
        btnDodaj.setForeground(Color.WHITE);

        panelUnos.add(txtKupac);
        panelUnos.add(cbVrstaDrveta);
        panelUnos.add(txtKolicina);
        panelUnos.add(txtCijenaPoM3);
        panelUnos.add(btnDodaj);

        add(panelUnos, BorderLayout.NORTH);

       
        String[] kolone = {"ID", "Broj", "Kupac", "Vrsta građe", "Količina (m³)", "Ukupno (KM)", "Status"};
        tableModel = new DefaultTableModel(kolone, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabela = new JTable(tableModel);
        add(new JScrollPane(tabela), BorderLayout.CENTER);

        
        JPanel panelDole = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnIsporuka = new JButton("Označi kao Isporuka");
        btnObrisi = new JButton("Obriši narudžbu");

        
        if (!"ADMINISTRATOR".equalsIgnoreCase(trenutnaUloga)) {
            btnObrisi.setEnabled(false);
            btnObrisi.setToolTipText("Samo administrator može brisati zapise.");
        }

        panelDole.add(btnIsporuka);
        panelDole.add(btnObrisi);
        add(panelDole, BorderLayout.SOUTH);

        
        ucitajPodatkeIzBaze();

        
        btnDodaj.addActionListener((ActionEvent e) -> dodajNarudzbuUBazu());

        btnIsporuka.addActionListener((ActionEvent e) -> promijeniStatus());

        btnObrisi.addActionListener((ActionEvent e) -> obrisiNarudzbuIzBaze());
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    private void ucitajPodatkeIzBaze() {
        tableModel.setRowCount(0);
        String sql = "SELECT * FROM narudzba ORDER BY id DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("broj"),
                        rs.getString("kupac"),
                        rs.getString("vrsta_drveta"),
                        rs.getDouble("kolicina"),
                        String.format("%.2f KM", rs.getDouble("ukupno")),
                        rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Greška pri čitanju baze: " + ex.getMessage());
        }
    }

    private void dodajNarudzbuUBazu() {
        String kupac = txtKupac.getText().trim();
        String gradja = (String) cbVrstaDrveta.getSelectedItem();
        String kolicinaStr = txtKolicina.getText().trim();
        String cijenaStr = txtCijenaPoM3.getText().trim();

        if (kupac.isEmpty() || kolicinaStr.isEmpty() || cijenaStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Popunite sva polja!");
            return;
        }

        try {
            double kolicina = Double.parseDouble(kolicinaStr);
            double cijena = Double.parseDouble(cijenaStr);
            double ukupno = kolicina * cijena;
            String broj = "NAR-" + (System.currentTimeMillis() % 10000);

            String sql = "INSERT INTO narudzba (broj, kupac, vrsta_drveta, kolicina, ukupno, status) VALUES (?, ?, ?, ?, ?, 'NA ČEKANJU')";

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, broj);
                ps.setString(2, kupac);
                ps.setString(3, gradja);
                ps.setDouble(4, kolicina);
                ps.setDouble(5, ukupno);
                ps.executeUpdate();

                ucitajPodatkeIzBaze();
                txtKupac.setText("");
                txtKolicina.setText("");
                txtCijenaPoM3.setText("");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Količina i cijena moraju biti brojevi!");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Greška pri upisu u bazu: " + ex.getMessage());
        }
    }

    private void promijeniStatus() {
        int red = tabela.getSelectedRow();
        if (red == -1) {
            JOptionPane.showMessageDialog(this, "Označite red u tabeli.");
            return;
        }

        int id = (int) tableModel.getValueAt(red, 0);
        String sql = "UPDATE narudzba SET status = 'ISPORUČENO' WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            ucitajPodatkeIzBaze();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Greška: " + ex.getMessage());
        }
    }

    private void obrisiNarudzbuIzBaze() {
        int red = tabela.getSelectedRow();
        if (red == -1) {
            JOptionPane.showMessageDialog(this, "Označite red u tabeli.");
            return;
        }

        int id = (int) tableModel.getValueAt(red, 0);
        String sql = "DELETE FROM narudzba WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            ucitajPodatkeIzBaze();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Greška pri brisanju: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();
        Object[] loginForm = {
                "Korisničko ime:", userField,
                "Lozinka:", passField
        };

        int option = JOptionPane.showConfirmDialog(null, loginForm, "Pilana - Prijava na sistem", JOptionPane.OK_CANCEL_OPTION);
        if (option != JOptionPane.OK_OPTION) {
            System.exit(0);
        }

        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();

       
        String sql = "SELECT uloga FROM korisnik WHERE korisnicko_ime = ? AND lozinka = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String uloga = rs.getString("uloga");
                SwingUtilities.invokeLater(() -> new PilanaApp(uloga, username).setVisible(true));
            } else {
                JOptionPane.showMessageDialog(null, "Pogrešno korisničko ime ili lozinka!", "Greška", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "Nije moguće uspostaviti vezu sa MySQL bazom.\nProvjerite da li je XAMPP upaljen.", "Greška konekcije", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }
}