import Foundation
import NoopBandCore

@main
struct ConformanceMain {
    static func main() async {
        guard CommandLine.arguments.count == 2 else {
            FileHandle.standardError.write(
                Data("usage: noop-band-conformance <scenario|--list>\n".utf8)
            )
            Foundation.exit(2)
        }

        do {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.sortedKeys]
            let argument = CommandLine.arguments[1]
            let data: Data
            if argument == "--list" {
                data = try encoder.encode(
                    BandConformanceRunner.automatedScenarios
                )
            } else {
                data = try await encoder.encode(
                    BandConformanceRunner.run(argument)
                )
            }
            FileHandle.standardOutput.write(data)
            FileHandle.standardOutput.write(Data("\n".utf8))
        } catch let error as BandFailureCategory {
            FileHandle.standardError.write(
                Data("conformance failed: \(error.rawValue)\n".utf8)
            )
            Foundation.exit(1)
        } catch {
            FileHandle.standardError.write(
                Data("conformance failed: internalFailure\n".utf8)
            )
            Foundation.exit(1)
        }
    }
}
