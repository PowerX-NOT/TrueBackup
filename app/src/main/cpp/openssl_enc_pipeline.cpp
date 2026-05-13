// Host / reference sample — same pipeline as `openssl enc -aes-256-cbc -salt -pbkdf2` after `tar`.
// On Android, TrueBackup builds plain `.tar` in Kotlin then encrypts with [OpenSslEncCompat] (no Termux binary).

#include <cstdlib>
#include <iostream>
#include <string>

int main(int argc, char** argv) {
    if (argc < 4) {
        std::cerr << "usage: " << (argv[0] ? argv[0] : "openssl_enc_pipeline")
                  << " <folder> <output.tar.enc> <passphrase>\n";
        return 2;
    }
    const std::string folder = argv[1];
    const std::string output = argv[2];
    const std::string pass = argv[3];

    std::string cmd =
        "tar -cvf - '" + folder + "'" +
        " | openssl enc -aes-256-cbc -salt -pbkdf2 -out '" + output + "'" +
        " -k '" + pass + "'";

    std::cout << "Encrypting folder: " << folder << std::endl;

    int result = std::system(cmd.c_str());

    if (result == 0) {
        std::cout << "Created: " << output << std::endl;
    } else {
        std::cout << "Encryption failed!" << std::endl;
    }

    return result == 0 ? 0 : 1;
}
